package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApplyApprovalDecisionService implements ApplyApprovalDecisionUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;
    private final AuditEventPublisher auditEventPublisher;
    private final SalaryAdvanceOfferCalculator salaryAdvanceOfferCalculator = new SalaryAdvanceOfferCalculator();

    public ApplyApprovalDecisionService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationLifecycleHistoryRecorder historyRecorder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceOfferPolicyRepository = salaryAdvanceOfferPolicyRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.historyRecorder = historyRecorder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto applyApprovalDecision(ApplyApprovalDecisionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.decisionId(), "decisionId must not be null");
        Objects.requireNonNull(command.reviewRecommendationId(), "reviewRecommendationId must not be null");
        Objects.requireNonNull(command.approverUserId(), "approverUserId must not be null");
        Objects.requireNonNull(command.action(), "action must not be null");
        Objects.requireNonNull(command.decidedAt(), "decidedAt must not be null");
        Objects.requireNonNull(command.operationId(), "operationId must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        ActionActor actor = ActionActor.user(command.approverUserId());
        LoanApplicationTransitionResult decisionTransition = loanApplication.applyApprovalDecisionWithTransition(command.action());
        LoanApplicationTransitionResult acceptanceTransition = null;
        LoanApplication transitionedApplication = decisionTransition.loanApplication();

        if (shouldGenerateSalaryAdvanceOffer(loanApplication, command.action())) {
            ApprovedOffer approvedOffer = generateSalaryAdvanceOffer(loanApplication, command);
            approvedOfferRepository.save(approvedOffer);
            acceptanceTransition = transitionedApplication.markCustomerAcceptancePendingWithTransition();
            transitionedApplication = acceptanceTransition.loanApplication();
            auditEventPublisher.publish(new AuditRecordRequestedEvent(
                    command.operationId(), (short) 2, actor, AuditEntityType.APPROVED_OFFER, approvedOffer.id(),
                    AuditAction.APPROVED_OFFER_GENERATED,
                    List.of(new AuditPayloadEntry(AuditPayloadKey.SOURCE_POLICY_ID, approvedOffer.sourceLoanProductPolicyId().toString())),
                    command.decidedAt()
            ));
        }

        LoanApplication savedApplication = loanApplicationRepository.save(transitionedApplication);
        historyRecorder.record(
                command.operationId(), actor, command.reason(), command.decidedAt(), decisionTransition, acceptanceTransition
        );

        if (shouldReleaseSalaryAdvanceReservation(loanApplication, command.action())) {
            salaryAdvanceReservationReleaseService.releaseReservationOnce(
                    loanApplication, command.decidedAt(), command.operationId(), actor, (short) 2
            );
        }

        return new LoanApplicationReviewDto(savedApplication.id(), savedApplication.status().name());
    }

    private boolean shouldGenerateSalaryAdvanceOffer(LoanApplication loanApplication, LoanApprovalDecisionAction action) {
        return loanApplication.productCode() == ProductCode.SALARY_ADVANCE && action == LoanApprovalDecisionAction.APPROVE;
    }

    private ApprovedOffer generateSalaryAdvanceOffer(LoanApplication loanApplication, ApplyApprovalDecisionCommand command) {
        SalaryAdvanceOfferPolicy policy = salaryAdvanceOfferPolicyRepository.findActiveDefaultPolicy()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "PRODUCT_POLICY_INVALID",
                        "Salary Advance active default offer policy was not found."
                ));
        return salaryAdvanceOfferCalculator.generate(
                UUID.randomUUID(), loanApplication.id(), policy, loanApplication.requestedAmount(),
                loanApplication.requestedTermMonths(), command.decidedAt()
        );
    }

    private boolean shouldReleaseSalaryAdvanceReservation(LoanApplication loanApplication, LoanApprovalDecisionAction action) {
        return loanApplication.productCode() == ProductCode.SALARY_ADVANCE && action == LoanApprovalDecisionAction.REJECT;
    }
}

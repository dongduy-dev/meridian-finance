package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionFact;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ReservationReleaseTrigger;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApplyApprovalDecisionService implements ApplyApprovalDecisionUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final SalaryAdvanceOfferCalculator salaryAdvanceOfferCalculator = new SalaryAdvanceOfferCalculator();

    public ApplyApprovalDecisionService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceOfferPolicyRepository = salaryAdvanceOfferPolicyRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
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
        Objects.requireNonNull(command.operationContext(), "operationContext must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        LoanApplicationTransitionResult decisionTransition = loanApplication.applyApprovalDecision(command.action());
        LoanApplication transitionedApplication = decisionTransition.loanApplication();
        List<LoanApplicationTransitionFact> transitionFacts = new ArrayList<>(decisionTransition.facts());
        ApprovedOffer savedApprovedOffer = null;

        if (shouldGenerateSalaryAdvanceOffer(loanApplication, command.action())) {
            ApprovedOffer approvedOffer = generateSalaryAdvanceOffer(loanApplication, command);
            savedApprovedOffer = approvedOfferRepository.save(approvedOffer);
            LoanApplicationTransitionResult acceptancePendingTransition =
                    transitionedApplication.markCustomerAcceptancePending();
            transitionedApplication = acceptancePendingTransition.loanApplication();
            transitionFacts.addAll(acceptancePendingTransition.facts());
        }
        if (shouldReleaseSalaryAdvanceReservation(loanApplication, command.action())) {
            salaryAdvanceReservationReleaseService.releaseReservationOnce(
                    loanApplication,
                    command.operationContext(),
                    ReservationReleaseTrigger.APPROVAL_REJECTION
            );
        }

        LoanApplication savedApplication = loanApplicationRepository.save(transitionedApplication);
        transitionRecorder.record(command.operationContext(), transitionFacts, command.reason());
        if (savedApprovedOffer != null) {
            businessAuditPublisher.publish(BusinessAuditEvent.single(
                    command.operationContext(),
                    new BusinessAuditEntry(
                            BusinessAuditAction.APPROVED_OFFER_GENERATED,
                            BusinessAuditEntityType.APPROVED_OFFER,
                            savedApprovedOffer.id(),
                            BusinessAuditPayload.builder()
                                    .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, savedApplication.id())
                                    .put(BusinessAuditPayloadKey.OFFER_STATUS, savedApprovedOffer.status())
                                    .build()
                    )
            ));
        }

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name()
        );
    }

    private boolean shouldGenerateSalaryAdvanceOffer(
            LoanApplication loanApplication,
            LoanApprovalDecisionAction action
    ) {
        return loanApplication.productCode() == ProductCode.SALARY_ADVANCE
                && action == LoanApprovalDecisionAction.APPROVE;
    }

    private ApprovedOffer generateSalaryAdvanceOffer(
            LoanApplication loanApplication,
            ApplyApprovalDecisionCommand command
    ) {
        SalaryAdvanceOfferPolicy policy = salaryAdvanceOfferPolicyRepository.findActiveDefaultPolicy()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "PRODUCT_POLICY_INVALID",
                        "Salary Advance active default offer policy was not found."
                ));

        return salaryAdvanceOfferCalculator.generate(
                UUID.randomUUID(),
                loanApplication.id(),
                policy,
                loanApplication.requestedAmount(),
                loanApplication.requestedTermMonths(),
                command.operationContext().occurredAt()
        );
    }

    private boolean shouldReleaseSalaryAdvanceReservation(
            LoanApplication loanApplication,
            LoanApprovalDecisionAction action
    ) {
        return loanApplication.productCode() == ProductCode.SALARY_ADVANCE
                && action == LoanApprovalDecisionAction.REJECT;
    }
}

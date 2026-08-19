package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionFact;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ReservationReleaseTrigger;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanOfferPolicy;
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.loan.domain.service.UnsecuredConsumerLoanOfferCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApplyApprovalDecisionService implements ApplyApprovalDecisionUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final CustomerCorrectionWorkflowService customerCorrectionWorkflowService;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository;
    private final UnsecuredConsumerLoanOfferPolicyRepository unsecuredConsumerLoanOfferPolicyRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final CollateralLoanApprovalExecutionGuard collateralLoanApprovalExecutionGuard;
    private final SalaryAdvanceOfferCalculator salaryAdvanceOfferCalculator = new SalaryAdvanceOfferCalculator();
    private final UnsecuredConsumerLoanOfferCalculator unsecuredConsumerLoanOfferCalculator =
            new UnsecuredConsumerLoanOfferCalculator();

    @Autowired
    public ApplyApprovalDecisionService(
            LoanApplicationRepository loanApplicationRepository,
            LoanReviewCycleRepository reviewCycleRepository,
            CustomerCorrectionWorkflowService customerCorrectionWorkflowService,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceOfferPolicyRepository salaryAdvanceOfferPolicyRepository,
            UnsecuredConsumerLoanOfferPolicyRepository unsecuredConsumerLoanOfferPolicyRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            CollateralLoanApprovalExecutionGuard collateralLoanApprovalExecutionGuard
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.customerCorrectionWorkflowService = customerCorrectionWorkflowService;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceOfferPolicyRepository = salaryAdvanceOfferPolicyRepository;
        this.unsecuredConsumerLoanOfferPolicyRepository = unsecuredConsumerLoanOfferPolicyRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.collateralLoanApprovalExecutionGuard = collateralLoanApprovalExecutionGuard;
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
        validateOperationContext(command);

        loanApplicationRepository.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        collateralLoanApprovalExecutionGuard.requireExecutionSupported(loanApplication);

        LoanApplicationReviewCycle activeCycle = reviewCycleRepository
                .findActiveByLoanApplicationIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_CYCLE_REQUIRED", "An active review cycle is required."));
        if (command.reviewCycleId() != null && !activeCycle.id().equals(command.reviewCycleId())) {
            throw new BusinessStateConflictException(
                    "STALE_REVIEW_CYCLE", "The decision review cycle is no longer active.");
        }

        LoanApplicationTransitionResult decisionTransition = loanApplication.applyApprovalDecision(command.action());
        LoanApplication transitionedApplication = decisionTransition.loanApplication();
        List<LoanApplicationTransitionFact> transitionFacts = new ArrayList<>(decisionTransition.facts());
        ApprovedOffer savedApprovedOffer = null;

        if (shouldGenerateApprovedOffer(loanApplication, command.action())) {
            ApprovedOffer approvedOffer = generateApprovedOffer(loanApplication, command);
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

        switch (command.action()) {
            case APPROVE, REJECT -> reviewCycleRepository.save(
                    activeCycle.complete(command.operationContext().occurredAt())
            );
            case RETURN_TO_LOAN_OFFICER_REVIEW -> {
                reviewCycleRepository.save(activeCycle.supersede(command.operationContext().occurredAt()));
                reviewCycleRepository.save(LoanApplicationReviewCycle.active(
                        UUID.randomUUID(),
                        loanApplication.id(),
                        reviewCycleRepository.nextCycleNumber(loanApplication.id()),
                        command.operationContext().occurredAt()
                ));
            }
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> {
                Objects.requireNonNull(command.reasonCode(), "reasonCode must not be null");
                Objects.requireNonNull(command.correctionPlan(), "correctionPlan must not be null");
                customerCorrectionWorkflowService.createFromDecision(
                        loanApplication, activeCycle, command.action().name(),
                        command.reasonCode(), command.correctionPlan(), command.operationContext()
                );
            }
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

    private void validateOperationContext(ApplyApprovalDecisionCommand command) {
        if (command.operationContext().actorType() != ActorType.USER
                || !command.approverUserId().equals(command.operationContext().actorUserId())
                || !command.decidedAt().equals(command.operationContext().occurredAt())) {
            throw new BusinessRuleViolationException(
                    "INVALID_OPERATION_CONTEXT",
                    "Approval operation context must match the authoritative approval decision record."
            );
        }
    }

    private boolean shouldGenerateApprovedOffer(
            LoanApplication loanApplication,
            LoanApprovalDecisionAction action
    ) {
        return (loanApplication.productCode() == ProductCode.SALARY_ADVANCE
                || loanApplication.productCode() == ProductCode.UNSECURED_CONSUMER_LOAN)
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

    private ApprovedOffer generateApprovedOffer(
            LoanApplication loanApplication,
            ApplyApprovalDecisionCommand command
    ) {
        return switch (loanApplication.productCode()) {
            case SALARY_ADVANCE -> generateSalaryAdvanceOffer(loanApplication, command);
            case UNSECURED_CONSUMER_LOAN -> generateUnsecuredConsumerLoanOffer(loanApplication, command);
            case COLLATERAL_LOAN -> throw new IllegalStateException(
                    "Collateral Loan cannot enter executable approved-offer generation."
            );
        };
    }

    private ApprovedOffer generateUnsecuredConsumerLoanOffer(
            LoanApplication loanApplication,
            ApplyApprovalDecisionCommand command
    ) {
        UnsecuredConsumerLoanOfferPolicy policy = unsecuredConsumerLoanOfferPolicyRepository
                .findActiveDefaultPolicy()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "PRODUCT_POLICY_INVALID",
                        "Unsecured Consumer Loan active default offer policy was not found."
                ));

        return unsecuredConsumerLoanOfferCalculator.generate(
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

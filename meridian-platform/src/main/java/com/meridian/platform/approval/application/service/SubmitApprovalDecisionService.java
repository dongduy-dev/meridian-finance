package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionEventPublisher;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ApprovalLoanReviewCyclePort;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitApprovalDecisionService implements SubmitApprovalDecisionUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final ApprovalLoanReviewCyclePort loanReviewCyclePort;
    private final ApprovalDecisionEventPublisher approvalDecisionEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;
    private final CorrectionPlanPolicy correctionPlanPolicy = new CorrectionPlanPolicy();

    public SubmitApprovalDecisionService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ApprovalDecisionRepository approvalDecisionRepository,
            ApprovalLoanReviewCyclePort loanReviewCyclePort,
            ApprovalDecisionEventPublisher approvalDecisionEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.loanReviewCyclePort = loanReviewCyclePort;
        this.approvalDecisionEventPublisher = approvalDecisionEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApprovalDecisionDto submitApprovalDecision(
            UUID loanApplicationId,
            ApprovalDecisionRequest request
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.action(), "action must not be null");

        ReviewRecommendation latestRecommendation = reviewRecommendationRepository
                .findLatestByLoanApplicationId(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_RECOMMENDATION_REQUIRED",
                        "An approval decision requires a Loan Officer recommendation."
                ));
        validateCorrectionContract(request, latestRecommendation);

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        validateMakerChecker(latestRecommendation, currentUser);

        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );

        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(),
                loanApplicationId,
                latestRecommendation.id(),
                currentUser.userId(),
                request.action(),
                request.reason(),
                request.reasonCode(),
                request.internalNotes(),
                now
        );

        ApprovalDecision savedDecision = approvalDecisionRepository.save(decision);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.APPROVAL_DECISION_RECORDED,
                        BusinessAuditEntityType.APPROVAL_DECISION,
                        savedDecision.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, savedDecision.loanApplicationId())
                                .put(BusinessAuditPayloadKey.REVIEW_RECOMMENDATION_ID, savedDecision.reviewRecommendationId())
                                .put(BusinessAuditPayloadKey.APPROVAL_DECISION_ACTION, savedDecision.action())
                                .build()
                )
        ));
        approvalDecisionEventPublisher.publish(approvalMapper.toRecordedEvent(
                savedDecision, latestRecommendation.reviewCycleId(), operationContext, request.correctionPlan()
        ));

        return approvalMapper.toDto(savedDecision);
    }

    private void validateCorrectionContract(
            ApprovalDecisionRequest request,
            ReviewRecommendation latestRecommendation
    ) {
        if (request.action() == ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION) {
            UUID activeCycleId = loanReviewCyclePort.findActiveReviewCycleId(
                            latestRecommendation.loanApplicationId())
                    .orElseThrow(() -> new BusinessStateConflictException(
                            "REVIEW_CYCLE_REQUIRED", "An active review cycle is required."));
            if (!activeCycleId.equals(request.expectedReviewCycleId())
                    || !activeCycleId.equals(latestRecommendation.reviewCycleId())) {
                throw new BusinessStateConflictException(
                        "STALE_REVIEW_CYCLE", "The expected review cycle is no longer active.");
            }
            if (request.reasonCode() == null || request.reason() != null) {
                throw new BusinessRuleViolationException(
                        "INVALID_CORRECTION_PLAN",
                        "Revision decisions require a controlled reason code and no free-text reason."
                );
            }
            correctionPlanPolicy.validateMixedCorrection(request.correctionPlan());
            return;
        }
        if (request.expectedReviewCycleId() != null
                || request.reasonCode() != null
                || request.correctionPlan() != null) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Correction fields are allowed only for revision-producing actions."
            );
        }
    }

    private void validateMakerChecker(
            ReviewRecommendation latestRecommendation,
            AuthenticatedUser currentUser
    ) {
        if (latestRecommendation.loanOfficerUserId().equals(currentUser.userId())) {
            throw new BusinessRuleViolationException(
                    "MAKER_CHECKER_VIOLATION",
                    "Approver must be different from the Loan Officer who submitted the recommendation."
            );
        }
    }
}

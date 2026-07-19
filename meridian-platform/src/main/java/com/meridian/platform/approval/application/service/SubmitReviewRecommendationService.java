package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalLoanReviewCyclePort;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitReviewRecommendationService implements SubmitReviewRecommendationUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ApprovalLoanReviewCyclePort loanReviewCyclePort;
    private final ReviewRecommendationEventPublisher reviewRecommendationEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;
    private final CorrectionPlanPolicy correctionPlanPolicy = new CorrectionPlanPolicy();

    public SubmitReviewRecommendationService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ApprovalLoanReviewCyclePort loanReviewCyclePort,
            ReviewRecommendationEventPublisher reviewRecommendationEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.loanReviewCyclePort = loanReviewCyclePort;
        this.reviewRecommendationEventPublisher = reviewRecommendationEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReviewRecommendationDto submitReviewRecommendation(
            UUID loanApplicationId,
            ReviewRecommendationRequest request
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.action(), "action must not be null");
        rejectUnavailableRevisionAction(request);
        UUID reviewCycleId = loanReviewCyclePort.findActiveReviewCycleId(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "REVIEW_CYCLE_REQUIRED", "An active review cycle is required."));
        validateCorrectionContract(request, reviewCycleId);

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );

        ReviewRecommendation recommendation = ReviewRecommendation.recorded(
                UUID.randomUUID(),
                loanApplicationId,
                reviewCycleId,
                currentUser.userId(),
                request.action(),
                request.reason(),
                request.reasonCode(),
                request.internalNotes(),
                now
        );

        ReviewRecommendation savedRecommendation = reviewRecommendationRepository.save(recommendation);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.REVIEW_RECOMMENDATION_RECORDED,
                        BusinessAuditEntityType.REVIEW_RECOMMENDATION,
                        savedRecommendation.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, savedRecommendation.loanApplicationId())
                                .put(BusinessAuditPayloadKey.REVIEW_RECOMMENDATION_ACTION, savedRecommendation.action())
                                .build()
                )
        ));
        reviewRecommendationEventPublisher.publish(approvalMapper.toRecordedEvent(
                savedRecommendation, operationContext, request.correctionPlan()
        ));

        return approvalMapper.toDto(savedRecommendation);
    }

    private void rejectUnavailableRevisionAction(ReviewRecommendationRequest request) {
        if (request.action() == ReviewRecommendationAction.REQUEST_STAFF_CORRECTION) {
            throw new BusinessStateConflictException(
                    "REVISION_WORKFLOW_NOT_AVAILABLE",
                    "Staff correction workflow is not available yet."
            );
        }
    }

    private void validateCorrectionContract(ReviewRecommendationRequest request, UUID activeCycleId) {
        if (request.action() == ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION) {
            if (!activeCycleId.equals(request.expectedReviewCycleId())) {
                throw new BusinessStateConflictException(
                        "STALE_REVIEW_CYCLE",
                        "The expected review cycle is no longer active."
                );
            }
            if (request.reasonCode() == null || request.reason() != null) {
                throw new com.meridian.platform.shared.domain.exception.BusinessRuleViolationException(
                        "INVALID_CORRECTION_PLAN",
                        "Customer revision requires a controlled reason code and no free-text reason."
                );
            }
            correctionPlanPolicy.validateCustomerRevision(request.correctionPlan());
            return;
        }
        if (request.expectedReviewCycleId() != null
                || request.reasonCode() != null
                || request.correctionPlan() != null) {
            throw new com.meridian.platform.shared.domain.exception.BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Correction fields are allowed only for revision-producing actions."
            );
        }
    }
}

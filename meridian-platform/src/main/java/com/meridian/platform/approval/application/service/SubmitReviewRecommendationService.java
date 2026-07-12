package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitReviewRecommendationService implements SubmitReviewRecommendationUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ReviewRecommendationEventPublisher reviewRecommendationEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;

    public SubmitReviewRecommendationService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ReviewRecommendationEventPublisher reviewRecommendationEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
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
                currentUser.userId(),
                request.action(),
                request.reason(),
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
        reviewRecommendationEventPublisher.publish(approvalMapper.toRecordedEvent(savedRecommendation, operationContext));

        return approvalMapper.toDto(savedRecommendation);
    }
}

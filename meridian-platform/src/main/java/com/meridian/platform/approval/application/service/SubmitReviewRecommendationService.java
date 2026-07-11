package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitReviewRecommendationService implements SubmitReviewRecommendationUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ReviewRecommendationEventPublisher reviewRecommendationEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public SubmitReviewRecommendationService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ReviewRecommendationEventPublisher reviewRecommendationEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper,
            AuditEventPublisher auditEventPublisher,
            Clock clock
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.reviewRecommendationEventPublisher = reviewRecommendationEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReviewRecommendationDto submitReviewRecommendation(UUID loanApplicationId, ReviewRecommendationRequest request) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.action(), "action must not be null");

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID operationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        ReviewRecommendation recommendation = ReviewRecommendation.recorded(
                UUID.randomUUID(), loanApplicationId, currentUser.userId(), request.action(), request.reason(),
                request.internalNotes(), now
        );
        ReviewRecommendation savedRecommendation = reviewRecommendationRepository.save(recommendation);
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId, (short) 1, ActionActor.user(currentUser.userId()), AuditEntityType.REVIEW_RECOMMENDATION,
                savedRecommendation.id(), AuditAction.REVIEW_RECOMMENDATION_RECORDED,
                List.of(new AuditPayloadEntry(AuditPayloadKey.RECOMMENDATION_ACTION, savedRecommendation.action().name())),
                savedRecommendation.submittedAt()
        ));
        reviewRecommendationEventPublisher.publish(approvalMapper.toRecordedEvent(savedRecommendation, operationId));
        return approvalMapper.toDto(savedRecommendation);
    }
}

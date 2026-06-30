package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubmitReviewRecommendationService implements SubmitReviewRecommendationUseCase {

    private final ReviewRecommendationRepository reviewRecommendationRepository;
    private final ReviewRecommendationEventPublisher reviewRecommendationEventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalMapper approvalMapper;

    public SubmitReviewRecommendationService(
            ReviewRecommendationRepository reviewRecommendationRepository,
            ReviewRecommendationEventPublisher reviewRecommendationEventPublisher,
            CurrentUserProvider currentUserProvider,
            ApprovalMapper approvalMapper
    ) {
        this.reviewRecommendationRepository = reviewRecommendationRepository;
        this.reviewRecommendationEventPublisher = reviewRecommendationEventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.approvalMapper = approvalMapper;
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
        ReviewRecommendation recommendation = ReviewRecommendation.recorded(
                UUID.randomUUID(),
                loanApplicationId,
                currentUser.userId(),
                request.action(),
                request.reason(),
                request.internalNotes(),
                LocalDateTime.now()
        );

        ReviewRecommendation savedRecommendation = reviewRecommendationRepository.save(recommendation);
        reviewRecommendationEventPublisher.publish(approvalMapper.toRecordedEvent(savedRecommendation));

        return approvalMapper.toDto(savedRecommendation);
    }
}

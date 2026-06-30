package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/review-recommendations")
public class ReviewRecommendationController {

    private final SubmitReviewRecommendationUseCase submitReviewRecommendationUseCase;

    public ReviewRecommendationController(SubmitReviewRecommendationUseCase submitReviewRecommendationUseCase) {
        this.submitReviewRecommendationUseCase = submitReviewRecommendationUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('approval:recommend')")
    public ReviewRecommendationDto submitReviewRecommendation(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ReviewRecommendationRequest request
    ) {
        return submitReviewRecommendationUseCase.submitReviewRecommendation(loanApplicationId, request);
    }
}

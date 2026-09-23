package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDto;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewPageDto;
import com.meridian.platform.partner.application.port.in.DecidePartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEligibilityReviewUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/partner-eligibility-reviews")
public class PartnerEligibilityReviewController {

    private final QueryPartnerEligibilityReviewUseCase queryReviews;
    private final DecidePartnerEligibilityReviewUseCase decideReview;

    public PartnerEligibilityReviewController(
            QueryPartnerEligibilityReviewUseCase queryReviews,
            DecidePartnerEligibilityReviewUseCase decideReview
    ) {
        this.queryReviews = queryReviews;
        this.decideReview = decideReview;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner:read')")
    public PartnerEligibilityReviewPageDto queryReviews(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryReviews.queryReviews(status, page, size);
    }

    @GetMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('partner:read')")
    public PartnerEligibilityReviewDto queryReview(@PathVariable UUID reviewId) {
        return queryReviews.queryReview(reviewId);
    }

    @PostMapping("/{reviewId}/decision")
    @PreAuthorize("hasAuthority('partner:manage')")
    public PartnerEligibilityReviewDto decide(
            @PathVariable UUID reviewId,
            @Valid @RequestBody PartnerEligibilityReviewDecisionRequest request
    ) {
        return decideReview.decide(reviewId, request);
    }
}

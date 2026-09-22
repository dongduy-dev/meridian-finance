package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDto;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewPageDto;

import java.util.UUID;

public interface QueryPartnerEligibilityReviewUseCase {
    PartnerEligibilityReviewPageDto queryReviews(String status, int page, int size);
    PartnerEligibilityReviewDto queryReview(UUID reviewId);
}

package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDto;

import java.util.UUID;

public interface DecidePartnerEligibilityReviewUseCase {
    PartnerEligibilityReviewDto decide(UUID reviewId, PartnerEligibilityReviewDecisionRequest request);
}

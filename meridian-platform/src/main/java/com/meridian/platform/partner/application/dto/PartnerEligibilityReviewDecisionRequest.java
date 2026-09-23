package com.meridian.platform.partner.application.dto;

import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewDecision;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PartnerEligibilityReviewDecisionRequest(
        @NotNull PartnerEligibilityReviewDecision outcome,
        UUID partnerEmployeeId,
        @NotNull PartnerEligibilityReviewReason reasonCode
) {
}

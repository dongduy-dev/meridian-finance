package com.meridian.platform.partner.domain.model;

public enum PartnerEligibilityReviewReason {
    CURRENT_EMPLOYEE_CONFIRMED,
    NO_ELIGIBLE_CURRENT_EMPLOYEE,
    IDENTITY_EVIDENCE_MISMATCH,
    INSUFFICIENT_SOURCE_EVIDENCE;

    public boolean supports(PartnerEligibilityReviewDecision decision) {
        return switch (decision) {
            case APPROVE -> this == CURRENT_EMPLOYEE_CONFIRMED;
            case REJECT -> this != CURRENT_EMPLOYEE_CONFIRMED;
        };
    }
}

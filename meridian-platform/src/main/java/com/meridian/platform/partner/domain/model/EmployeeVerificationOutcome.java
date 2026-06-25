package com.meridian.platform.partner.domain.model;

public enum EmployeeVerificationOutcome {
    MATCHED_ACTIVE,
    MATCHED_INACTIVE,
    NOT_FOUND,
    MULTIPLE_MATCHES,
    PENDING_MANUAL_REVIEW,
    MANUAL_REVIEW_APPROVED,
    MANUAL_REVIEW_REJECTED
}

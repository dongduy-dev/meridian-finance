package com.meridian.platform.loan.domain.model;

public enum SalaryAdvanceEmployeeVerificationOutcome {
    MATCHED_ACTIVE,
    MATCHED_INACTIVE,
    NOT_FOUND,
    MULTIPLE_MATCHES,
    PENDING_MANUAL_REVIEW,
    MANUAL_REVIEW_APPROVED,
    MANUAL_REVIEW_REJECTED
}

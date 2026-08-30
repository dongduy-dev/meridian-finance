package com.meridian.platform.loan.domain.model;

import java.util.Set;

public enum LoanApplicationStatus {
    DRAFT,
    SUBMITTED,
    VERIFICATION_PENDING,
    VERIFICATION_FAILED,
    DOCUMENTS_PENDING,
    UNDER_REVIEW,
    RETURNED_FOR_REVISION,
    RETURNED_TO_REVIEW,
    APPROVAL_PENDING,
    APPROVED,
    REJECTED,
    CUSTOMER_ACCEPTANCE_PENDING,
    CUSTOMER_DECLINED,
    CONTRACT_PENDING,
    DISBURSEMENT_PENDING,
    DISBURSED,
    CANCELLED,
    EXPIRED;

    private static final Set<LoanApplicationStatus> TERMINAL_STATUSES = Set.of(
            VERIFICATION_FAILED,
            REJECTED,
            CUSTOMER_DECLINED,
            DISBURSED,
            CANCELLED,
            EXPIRED
    );

    public boolean isLifecycleActive() {
        return !TERMINAL_STATUSES.contains(this);
    }

    public static Set<LoanApplicationStatus> blockingStatuses() {
        return Set.of(
                SUBMITTED,
                VERIFICATION_PENDING,
                DOCUMENTS_PENDING,
                UNDER_REVIEW,
                RETURNED_FOR_REVISION,
                RETURNED_TO_REVIEW,
                APPROVAL_PENDING,
                APPROVED,
                CUSTOMER_ACCEPTANCE_PENDING,
                CONTRACT_PENDING,
                DISBURSEMENT_PENDING
        );
    }
}

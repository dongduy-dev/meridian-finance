package com.meridian.platform.loan.domain.model;

import java.util.Objects;

public record LoanApplicationTransitionFact(
        LoanApplicationStatus fromStatus,
        LoanApplicationStatus toStatus,
        LoanApplicationTransitionAction action
) {
    public LoanApplicationTransitionFact {
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (fromStatus == null) {
            if (action != LoanApplicationTransitionAction.APPLICATION_SUBMITTED
                    || toStatus != LoanApplicationStatus.SUBMITTED) {
                throw new IllegalArgumentException(
                        "fromStatus may be null only for APPLICATION_SUBMITTED to SUBMITTED"
                );
            }
        } else if (fromStatus == toStatus) {
            throw new IllegalArgumentException("A transition must change status");
        }
    }
}
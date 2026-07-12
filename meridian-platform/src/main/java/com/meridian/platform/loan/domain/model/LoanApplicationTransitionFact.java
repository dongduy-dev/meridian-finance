package com.meridian.platform.loan.domain.model;

import java.util.Objects;
import java.util.UUID;

public record LoanApplicationTransitionFact(
        UUID loanApplicationId,
        LoanApplicationStatus fromStatus,
        LoanApplicationStatus toStatus,
        LoanApplicationTransitionAction action
) {

    public LoanApplicationTransitionFact {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (fromStatus != null && fromStatus == toStatus) {
            throw new IllegalArgumentException("Transition fact must represent a status change.");
        }
    }
}

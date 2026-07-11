package com.meridian.platform.loan.domain.model;

import java.util.Objects;
import java.util.Optional;

public record LoanApplicationTransitionResult(
        LoanApplication loanApplication,
        LoanApplicationTransitionFact transitionFact
) {
    public LoanApplicationTransitionResult {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
    }

    public static LoanApplicationTransitionResult changed(
            LoanApplication loanApplication,
            LoanApplicationTransitionFact transitionFact
    ) {
        return new LoanApplicationTransitionResult(
                loanApplication,
                Objects.requireNonNull(transitionFact, "transitionFact must not be null")
        );
    }

    public static LoanApplicationTransitionResult unchanged(LoanApplication loanApplication) {
        return new LoanApplicationTransitionResult(loanApplication, null);
    }

    public Optional<LoanApplicationTransitionFact> transition() {
        return Optional.ofNullable(transitionFact);
    }
}

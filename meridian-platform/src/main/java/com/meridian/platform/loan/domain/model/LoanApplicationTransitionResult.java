package com.meridian.platform.loan.domain.model;

import java.util.List;
import java.util.Objects;

public record LoanApplicationTransitionResult(
        LoanApplication loanApplication,
        List<LoanApplicationTransitionFact> facts
) {

    public LoanApplicationTransitionResult {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        facts = List.copyOf(facts);
    }

    public static LoanApplicationTransitionResult of(
            LoanApplication loanApplication,
            LoanApplicationTransitionFact fact
    ) {
        return new LoanApplicationTransitionResult(loanApplication, List.of(fact));
    }

    public static LoanApplicationTransitionResult unchanged(LoanApplication loanApplication) {
        return new LoanApplicationTransitionResult(loanApplication, List.of());
    }
}

package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanAccountClosure(
        UUID id,
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID requestId,
        UUID closedByUserId,
        LocalDateTime closedAt
) {

    public LoanAccountClosure {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId,
                "loanApplicationId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(closedByUserId, "closedByUserId must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }

    public static LoanAccountClosure recorded(
            UUID id,
            LoanAccount closedAccount,
            UUID requestId,
            UUID closedByUserId,
            LocalDateTime closedAt
    ) {
        Objects.requireNonNull(closedAccount, "closedAccount must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
        if (closedAccount.status() != LoanAccountStatus.CLOSED
                || closedAccount.repaymentBalance().totalOutstanding().signum() != 0
                || !closedAccount.updatedAt().equals(closedAt)) {
            throw new BusinessRuleViolationException(
                    "LOAN_ACCOUNT_CLOSURE_EVIDENCE_INVALID",
                    "Closure evidence requires the corresponding closed Loan Account state."
            );
        }
        return new LoanAccountClosure(
                Objects.requireNonNull(id, "id must not be null"),
                closedAccount.loanApplicationId(),
                closedAccount.id(),
                Objects.requireNonNull(requestId, "requestId must not be null"),
                Objects.requireNonNull(closedByUserId,
                        "closedByUserId must not be null"),
                closedAt
        );
    }

    @Override
    public String toString() {
        return "LoanAccountClosure[id=" + id
                + ", loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", administrativeEvidence=redacted]";
    }
}

package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ApprovedLoanSettlement(
        UUID id,
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID repaymentTransactionId,
        UUID requestId,
        BigDecimal settlementAmount,
        UUID approvedByUserId,
        LocalDateTime approvedAt
) {

    public ApprovedLoanSettlement {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId,
                "loanApplicationId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        Objects.requireNonNull(repaymentTransactionId,
                "repaymentTransactionId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(settlementAmount, "settlementAmount must not be null");
        Objects.requireNonNull(approvedByUserId,
                "approvedByUserId must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        if (settlementAmount.signum() <= 0
                || settlementAmount.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid("Settlement amount must be a positive whole VND amount.");
        }
    }

    public static ApprovedLoanSettlement from(
            UUID id,
            RepaymentTransaction transaction
    ) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        if (transaction.transactionType()
                != RepaymentTransactionType.APPROVED_SETTLEMENT) {
            throw invalid(
                    "Approved settlement evidence requires a settlement payment transaction."
            );
        }
        return new ApprovedLoanSettlement(
                Objects.requireNonNull(id, "id must not be null"),
                transaction.loanApplicationId(),
                transaction.loanAccountId(),
                transaction.id(),
                transaction.requestId(),
                transaction.receivedAmount(),
                transaction.recordedByUserId(),
                transaction.recordedAt()
        );
    }

    @Override
    public String toString() {
        return "ApprovedLoanSettlement[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", financialEvidence=redacted]";
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException(
                "LOAN_SETTLEMENT_EVIDENCE_INVALID",
                message
        );
    }
}

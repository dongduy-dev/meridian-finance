package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record LoanAccount(
        UUID id,
        UUID loanApplicationId,
        UUID loanContractId,
        UUID customerId,
        String accountNumber,
        LoanAccountStatus status,
        BigDecimal approvedPrincipal,
        int approvedTermMonths,
        BigDecimal totalInterest,
        BigDecimal feeAmount,
        BigDecimal totalRepaymentAmount,
        LocalDateTime activatedAt
) {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("LA-[0-9A-F]{32}");

    public LoanAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(loanContractId, "loanContractId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(approvedPrincipal, "approvedPrincipal must not be null");
        Objects.requireNonNull(totalInterest, "totalInterest must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(totalRepaymentAmount, "totalRepaymentAmount must not be null");
        Objects.requireNonNull(activatedAt, "activatedAt must not be null");

        if (!ACCOUNT_NUMBER_PATTERN.matcher(Objects.requireNonNull(
                accountNumber,
                "accountNumber must not be null"
        )).matches() || !accountNumber.equals(accountNumberFor(id))) {
            throw invalid("Loan Account number is invalid.");
        }
        requirePositiveWholeVnd(approvedPrincipal, "approvedPrincipal");
        if (approvedTermMonths <= 0) {
            throw invalid("approvedTermMonths must be positive.");
        }
        requireNonNegativeWholeVnd(totalInterest, "totalInterest");
        requireNonNegativeWholeVnd(feeAmount, "feeAmount");
        requirePositiveWholeVnd(totalRepaymentAmount, "totalRepaymentAmount");
        if (totalRepaymentAmount.compareTo(approvedPrincipal.add(totalInterest).add(feeAmount)) != 0) {
            throw invalid("Loan Account financial totals do not reconcile.");
        }
    }

    public static LoanAccount activate(UUID id, LoanContract contract, LocalDateTime activatedAt) {
        Objects.requireNonNull(contract, "contract must not be null");
        if (contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT) {
            throw new BusinessStateConflictException(
                    "LOAN_ACCOUNT_ACTIVATION_NOT_ALLOWED",
                    "A Loan Account can only be activated from a ready contract."
            );
        }
        ApprovedOfferFinancialTerms terms = contract.financialTerms();
        return new LoanAccount(
                Objects.requireNonNull(id, "id must not be null"),
                contract.loanApplicationId(),
                contract.id(),
                contract.disbursementBankAccount().customerId(),
                accountNumberFor(id),
                LoanAccountStatus.ACTIVE,
                terms.approvedPrincipal(),
                terms.approvedTermMonths(),
                terms.totalInterest(),
                terms.feeAmount(),
                terms.totalRepaymentAmount(),
                Objects.requireNonNull(activatedAt, "activatedAt must not be null")
        );
    }

    public static String accountNumberFor(UUID id) {
        return "LA-" + Objects.requireNonNull(id, "id must not be null")
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoanAccount[id=" + id + ", loanApplicationId=" + loanApplicationId
                + ", loanContractId=" + loanContractId + ", status=" + status
                + ", financialEvidence=redacted]";
    }

    private static void requirePositiveWholeVnd(BigDecimal value, String fieldName) {
        requireNonNegativeWholeVnd(value, fieldName);
        if (value.signum() <= 0) {
            throw invalid(fieldName + " must be positive.");
        }
    }

    private static void requireNonNegativeWholeVnd(BigDecimal value, String fieldName) {
        if (value.signum() < 0 || value.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid(fieldName + " must be a non-negative whole VND amount.");
        }
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("LOAN_ACCOUNT_INVALID", message);
    }
}

package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        LocalDateTime activatedAt,
        RepaymentBalance repaymentBalance,
        LocalDateTime updatedAt
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
        Objects.requireNonNull(repaymentBalance, "repaymentBalance must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

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
        repaymentBalance.validateAgainst(approvedPrincipal, totalInterest, feeAmount);
        if (updatedAt.isBefore(activatedAt)) {
            throw invalid("Loan Account update time cannot precede activation.");
        }
        if ((status == LoanAccountStatus.SETTLED || status == LoanAccountStatus.CLOSED)
                && repaymentBalance.totalOutstanding().signum() != 0) {
            throw invalid("Settled or closed Loan Account must have zero outstanding.");
        }
        if ((status == LoanAccountStatus.ACTIVE || status == LoanAccountStatus.OVERDUE)
                && repaymentBalance.totalOutstanding().signum() == 0) {
            throw invalid("Open Loan Account must have outstanding obligations.");
        }
    }

    public LoanAccount(
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
        this(
                id,
                loanApplicationId,
                loanContractId,
                customerId,
                accountNumber,
                status,
                approvedPrincipal,
                approvedTermMonths,
                totalInterest,
                feeAmount,
                totalRepaymentAmount,
                activatedAt,
                RepaymentBalance.initial(
                        approvedPrincipal,
                        totalInterest,
                        feeAmount,
                        activatedAt.toLocalDate()
                ),
                activatedAt
        );
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
                Objects.requireNonNull(activatedAt, "activatedAt must not be null"),
                RepaymentBalance.initial(
                        terms.approvedPrincipal(),
                        terms.totalInterest(),
                        terms.feeAmount(),
                        activatedAt.toLocalDate()
                ),
                activatedAt
        );
    }

    public LoanAccount withServicingState(
            RepaymentBalance newBalance,
            LoanAccountStatus newStatus,
            LocalDateTime changedAt
    ) {
        Objects.requireNonNull(newBalance, "newBalance must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (newStatus == LoanAccountStatus.CLOSED) {
            throw new BusinessStateConflictException(
                    "LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED",
                    "Administrative closure is outside repayment servicing."
            );
        }
        return new LoanAccount(
                id,
                loanApplicationId,
                loanContractId,
                customerId,
                accountNumber,
                newStatus,
                approvedPrincipal,
                approvedTermMonths,
                totalInterest,
                feeAmount,
                totalRepaymentAmount,
                activatedAt,
                newBalance,
                changedAt
        );
    }

    public LoanAccount closeAdministratively(LocalDateTime closedAt) {
        Objects.requireNonNull(closedAt, "closedAt must not be null");
        if (status != LoanAccountStatus.SETTLED
                || repaymentBalance.totalOutstanding().signum() != 0) {
            throw new BusinessStateConflictException(
                    "LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED",
                    "Administrative closure requires a settled Loan Account."
            );
        }
        if (closedAt.isBefore(updatedAt)) {
            throw invalid("Loan Account closure time cannot precede its last update.");
        }
        return new LoanAccount(
                id,
                loanApplicationId,
                loanContractId,
                customerId,
                accountNumber,
                LoanAccountStatus.CLOSED,
                approvedPrincipal,
                approvedTermMonths,
                totalInterest,
                feeAmount,
                totalRepaymentAmount,
                activatedAt,
                repaymentBalance,
                closedAt
        );
    }

    public LocalDate servicingEvaluationDate() {
        return repaymentBalance.servicingEvaluationDate();
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

package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record SalaryAdvanceLimitMovement(
        UUID id,
        UUID salaryAdvanceLimitId,
        UUID loanApplicationId,
        UUID loanAccountId,
        SalaryAdvanceLimitMovementType movementType,
        BigDecimal amount,
        LocalDateTime occurredAt,
        UUID repaymentTransactionId
) {

    public SalaryAdvanceLimitMovement {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(salaryAdvanceLimitId,
                "salaryAdvanceLimitId must not be null");
        Objects.requireNonNull(movementType, "movementType must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (movementType == SalaryAdvanceLimitMovementType.REPAID_RELEASED) {
            if (loanApplicationId == null || loanAccountId == null
                    || repaymentTransactionId == null
                    || amount.signum() <= 0
                    || amount.remainder(BigDecimal.ONE).signum() != 0) {
                throw invalidRepaymentRelease();
            }
        } else if (repaymentTransactionId != null) {
            throw invalidRepaymentRelease();
        }
    }

    public SalaryAdvanceLimitMovement(
            UUID id,
            UUID salaryAdvanceLimitId,
            UUID loanApplicationId,
            UUID loanAccountId,
            SalaryAdvanceLimitMovementType movementType,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        this(
                id,
                salaryAdvanceLimitId,
                loanApplicationId,
                loanAccountId,
                movementType,
                amount,
                occurredAt,
                null
        );
    }

    public static SalaryAdvanceLimitMovement initialized(
            UUID id,
            SalaryAdvanceLimit limit,
            LocalDateTime occurredAt
    ) {
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(limit, "limit must not be null").id(),
                null,
                null,
                SalaryAdvanceLimitMovementType.INITIALIZED,
                limit.totalLimit(),
                Objects.requireNonNull(occurredAt, "occurredAt must not be null")
        );
    }

    public static SalaryAdvanceLimitMovement refreshed(
            UUID id,
            SalaryAdvanceLimit limit,
            LocalDateTime occurredAt
    ) {
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(limit, "limit must not be null").id(),
                null,
                null,
                SalaryAdvanceLimitMovementType.REFRESHED,
                limit.totalLimit(),
                Objects.requireNonNull(occurredAt, "occurredAt must not be null")
        );
    }

    public static SalaryAdvanceLimitMovement reserved(
            UUID id,
            UUID salaryAdvanceLimitId,
            UUID loanApplicationId,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(salaryAdvanceLimitId, "salaryAdvanceLimitId must not be null"),
                Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null"),
                null,
                SalaryAdvanceLimitMovementType.RESERVED,
                Objects.requireNonNull(amount, "amount must not be null"),
                Objects.requireNonNull(occurredAt, "occurredAt must not be null")
        );
    }

    public static SalaryAdvanceLimitMovement reservationReleased(
            UUID id,
            UUID salaryAdvanceLimitId,
            UUID loanApplicationId,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(salaryAdvanceLimitId, "salaryAdvanceLimitId must not be null"),
                Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null"),
                null,
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED,
                Objects.requireNonNull(amount, "amount must not be null"),
                Objects.requireNonNull(occurredAt, "occurredAt must not be null")
        );
    }

    public static SalaryAdvanceLimitMovement disbursedToUsed(
            UUID id,
            UUID salaryAdvanceLimitId,
            UUID loanApplicationId,
            UUID loanAccountId,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0 || amount.remainder(BigDecimal.ONE).signum() != 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Disbursed-to-used amount must be a positive whole VND amount."
            );
        }
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(salaryAdvanceLimitId, "salaryAdvanceLimitId must not be null"),
                Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null"),
                Objects.requireNonNull(loanAccountId, "loanAccountId must not be null"),
                SalaryAdvanceLimitMovementType.DISBURSED_TO_USED,
                amount,
                Objects.requireNonNull(occurredAt, "occurredAt must not be null")
        );
    }

    public static SalaryAdvanceLimitMovement repaidReleased(
            UUID id,
            UUID salaryAdvanceLimitId,
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentTransactionId,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        return new SalaryAdvanceLimitMovement(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(
                        salaryAdvanceLimitId,
                        "salaryAdvanceLimitId must not be null"
                ),
                Objects.requireNonNull(
                        loanApplicationId,
                        "loanApplicationId must not be null"
                ),
                Objects.requireNonNull(loanAccountId, "loanAccountId must not be null"),
                SalaryAdvanceLimitMovementType.REPAID_RELEASED,
                Objects.requireNonNull(amount, "amount must not be null"),
                Objects.requireNonNull(occurredAt, "occurredAt must not be null"),
                Objects.requireNonNull(
                        repaymentTransactionId,
                        "repaymentTransactionId must not be null"
                )
        );
    }

    private static BusinessRuleViolationException invalidRepaymentRelease() {
        return new BusinessRuleViolationException(
                "REPAYMENT_RELEASE_EVIDENCE_INVALID",
                "Repayment-release movement evidence is invalid."
        );
    }
}

package com.meridian.platform.loan.domain.model;

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
        LocalDateTime occurredAt
) {

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
}

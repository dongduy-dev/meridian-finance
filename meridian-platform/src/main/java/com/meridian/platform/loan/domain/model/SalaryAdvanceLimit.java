package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record SalaryAdvanceLimit(
        UUID id,
        UUID customerId,
        UUID customerPartnerEmployeeLinkId,
        BigDecimal totalLimit,
        BigDecimal usedAmount,
        BigDecimal reservedAmount,
        BigDecimal availableAmount,
        SalaryAdvanceLimitStatus status,
        LocalDateTime lastRefreshedAt
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static SalaryAdvanceLimit initialized(
            UUID id,
            UUID customerId,
            UUID customerPartnerEmployeeLinkId,
            BigDecimal totalLimit,
            LocalDateTime lastRefreshedAt
    ) {
        requireNonNegative(totalLimit, "totalLimit");

        return new SalaryAdvanceLimit(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(customerId, "customerId must not be null"),
                Objects.requireNonNull(customerPartnerEmployeeLinkId, "customerPartnerEmployeeLinkId must not be null"),
                Objects.requireNonNull(totalLimit, "totalLimit must not be null"),
                ZERO,
                ZERO,
                totalLimit,
                SalaryAdvanceLimitStatus.ACTIVE,
                Objects.requireNonNull(lastRefreshedAt, "lastRefreshedAt must not be null")
        );
    }

    public SalaryAdvanceLimit reserve(BigDecimal amount) {
        requirePositive(amount, "amount");

        if (status != SalaryAdvanceLimitStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "SALARY_ADVANCE_LIMIT_UNAVAILABLE",
                    "Salary Advance limit is not active."
            );
        }

        if (availableAmount.compareTo(amount) < 0) {
            throw new BusinessRuleViolationException(
                    "INSUFFICIENT_AVAILABLE_LIMIT",
                    "Requested amount exceeds available Salary Advance limit."
            );
        }

        BigDecimal newReservedAmount = reservedAmount.add(amount);
        BigDecimal newAvailableAmount = availableAmount.subtract(amount);

        return new SalaryAdvanceLimit(
                id,
                customerId,
                customerPartnerEmployeeLinkId,
                totalLimit,
                usedAmount,
                newReservedAmount,
                newAvailableAmount,
                status,
                lastRefreshedAt
        );
    }

    public SalaryAdvanceLimit releaseReservation(BigDecimal amount) {
        requirePositive(amount, "amount");

        if (reservedAmount.compareTo(amount) < 0) {
            throw new BusinessRuleViolationException(
                    "SALARY_ADVANCE_RESERVATION_RELEASE_NOT_ALLOWED",
                    "Cannot release more Salary Advance reservation than currently reserved."
            );
        }

        return new SalaryAdvanceLimit(
                id,
                customerId,
                customerPartnerEmployeeLinkId,
                totalLimit,
                usedAmount,
                reservedAmount.subtract(amount),
                availableAmount.add(amount),
                status,
                lastRefreshedAt
        );
    }

    public SalaryAdvanceLimit convertReservedToUsed(BigDecimal amount) {
        requirePositiveWholeVnd(amount, "amount");
        requireReconciled();

        if (reservedAmount.compareTo(amount) < 0) {
            throw new BusinessRuleViolationException(
                    "SALARY_ADVANCE_RESERVATION_INVALID",
                    "Cannot convert more than the currently reserved Salary Advance amount."
            );
        }

        SalaryAdvanceLimit converted = new SalaryAdvanceLimit(
                id,
                customerId,
                customerPartnerEmployeeLinkId,
                totalLimit,
                usedAmount.add(amount),
                reservedAmount.subtract(amount),
                availableAmount,
                status,
                lastRefreshedAt
        );
        converted.requireReconciled();
        return converted;
    }

    public SalaryAdvanceLimit refreshTotalLimit(BigDecimal effectiveTotalLimit, LocalDateTime lastRefreshedAt) {
        requireNonNegative(effectiveTotalLimit, "effectiveTotalLimit");
        Objects.requireNonNull(lastRefreshedAt, "lastRefreshedAt must not be null");

        BigDecimal currentExposure = usedAmount.add(reservedAmount);
        if (currentExposure.compareTo(effectiveTotalLimit) > 0) {
            throw new BusinessRuleViolationException(
                    "INSUFFICIENT_AVAILABLE_LIMIT",
                    "Current Salary Advance exposure exceeds effective limit."
            );
        }

        return new SalaryAdvanceLimit(
                id,
                customerId,
                customerPartnerEmployeeLinkId,
                effectiveTotalLimit,
                usedAmount,
                reservedAmount,
                effectiveTotalLimit.subtract(currentExposure),
                status,
                lastRefreshedAt
        );
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    fieldName + " must be positive."
            );
        }
    }

    private static void requirePositiveWholeVnd(BigDecimal value, String fieldName) {
        requirePositive(value, fieldName);
        if (value.remainder(BigDecimal.ONE).signum() != 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    fieldName + " must be a whole VND amount."
            );
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(ZERO) < 0) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    fieldName + " must not be negative."
            );
        }
    }

    private void requireReconciled() {
        if (totalLimit.compareTo(usedAmount.add(reservedAmount).add(availableAmount)) != 0) {
            throw new BusinessRuleViolationException(
                    "SALARY_ADVANCE_LIMIT_STATE_INVALID",
                    "Salary Advance limit amounts do not reconcile."
            );
        }
    }
}

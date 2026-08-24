package com.meridian.platform.loan.domain.model.collateral;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record Collateral(
        UUID id,
        UUID loanApplicationId,
        CollateralType collateralType,
        String description,
        BigDecimal estimatedValue,
        String ownershipStatus,
        String conditionNote,
        LocalDateTime createdAt
) {

    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int OWNERSHIP_STATUS_MAX_LENGTH = 200;
    private static final int CONDITION_NOTE_MAX_LENGTH = 500;

    public Collateral {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(collateralType, "collateralType must not be null");
        Objects.requireNonNull(estimatedValue, "estimatedValue must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        description = normalizeRequired(description, "description", DESCRIPTION_MAX_LENGTH);
        ownershipStatus = normalizeRequired(
                ownershipStatus,
                "ownershipStatus",
                OWNERSHIP_STATUS_MAX_LENGTH
        );
        conditionNote = normalizeRequired(conditionNote, "conditionNote", CONDITION_NOTE_MAX_LENGTH);
        if (estimatedValue.compareTo(BigDecimal.ZERO) <= 0
                || estimatedValue.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("estimatedValue must be a positive whole VND amount");
        }
    }

    public static Collateral submitted(
            UUID id,
            LoanApplication loanApplication,
            CollateralType collateralType,
            String description,
            BigDecimal estimatedValue,
            String ownershipStatus,
            String conditionNote,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        if (loanApplication.productCode() != ProductCode.COLLATERAL_LOAN
                || loanApplication.productType() != ProductType.SECURED) {
            throw new IllegalArgumentException("Collateral facts require a Collateral Loan application.");
        }
        return new Collateral(
                id,
                loanApplication.id(),
                collateralType,
                description,
                estimatedValue,
                ownershipStatus,
                conditionNote,
                createdAt
        );
    }

    private static String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds its maximum length");
        }
        return normalized;
    }
}

package com.meridian.platform.loan.domain.service.collateral;

import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CollateralLoanApplicationPolicy {

    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int OWNERSHIP_STATUS_MAX_LENGTH = 200;
    private static final int CONDITION_NOTE_MAX_LENGTH = 500;
    private static final Set<Integer> ALLOWED_TERMS_MONTHS = Set.of(6, 12, 18, 24);

    public void validateProduct(LoanProduct loanProduct) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        if (loanProduct.productCode() != ProductCode.COLLATERAL_LOAN
                || loanProduct.productType() != ProductType.SECURED) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Loan product is not configured as Collateral Loan."
            );
        }
        if (!loanProduct.active()) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_INACTIVE",
                    "Collateral Loan product is inactive."
            );
        }
        if (loanProduct.minAmount() == null || loanProduct.maxAmount() == null
                || loanProduct.minAmount().compareTo(BigDecimal.ZERO) <= 0
                || loanProduct.maxAmount().compareTo(loanProduct.minAmount()) < 0) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Collateral Loan amount limits are invalid."
            );
        }
    }

    public void validateRequestedAmount(LoanProduct loanProduct, BigDecimal requestedAmount) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");
        if (requestedAmount.compareTo(loanProduct.minAmount()) < 0
                || requestedAmount.compareTo(loanProduct.maxAmount()) > 0
                || requestedAmount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Requested amount is outside Collateral Loan product limits or is not whole VND."
            );
        }
    }

    public void validateRequestedTerm(int requestedTermMonths) {
        if (!ALLOWED_TERMS_MONTHS.contains(requestedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Requested term is not allowed for Collateral Loan."
            );
        }
    }

    public void validateCollateralDetails(
            CollateralType collateralType,
            String description,
            BigDecimal estimatedValue,
            String ownershipStatus,
            String conditionNote
    ) {
        try {
            Objects.requireNonNull(collateralType, "collateralType must not be null");
            Objects.requireNonNull(estimatedValue, "estimatedValue must not be null");
            validateRequiredText(description, "description", DESCRIPTION_MAX_LENGTH);
            validateRequiredText(ownershipStatus, "ownershipStatus", OWNERSHIP_STATUS_MAX_LENGTH);
            validateRequiredText(conditionNote, "conditionNote", CONDITION_NOTE_MAX_LENGTH);
            if (estimatedValue.compareTo(BigDecimal.ZERO) <= 0
                    || estimatedValue.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("estimatedValue must be a positive whole VND amount");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidCollateralDetails();
        }
    }

    public Collateral createCollateral(
            UUID id,
            LoanApplication loanApplication,
            CollateralType collateralType,
            String description,
            BigDecimal estimatedValue,
            String ownershipStatus,
            String conditionNote,
            LocalDateTime createdAt
    ) {
        try {
            return Collateral.submitted(
                    id,
                    loanApplication,
                    collateralType,
                    description,
                    estimatedValue,
                    ownershipStatus,
                    conditionNote,
                    createdAt
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidCollateralDetails();
        }
    }

    private void validateRequiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds its maximum length");
        }
    }

    private BusinessRuleViolationException invalidCollateralDetails() {
        return new BusinessRuleViolationException(
                "INVALID_COLLATERAL_DETAILS",
                "Collateral facts must be complete, within technical limits, and use a positive whole-VND estimated value."
        );
    }
}

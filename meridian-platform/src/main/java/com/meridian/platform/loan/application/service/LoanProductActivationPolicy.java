package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductActivationPolicy {

    ProductCode supportedProduct();

    ProductActivationResult activate(ProductActivationCommand command);

    void validateCompletedActivation(CompletedActivationValidationCommand command);

    record ProductActivationCommand(
            LoanApplication loanApplication,
            LoanContract loanContract,
            LoanAccount loanAccount,
            UUID movementId,
            LocalDateTime occurredAt
    ) {
        public ProductActivationCommand {
            Objects.requireNonNull(loanApplication, "loanApplication must not be null");
            Objects.requireNonNull(loanContract, "loanContract must not be null");
            Objects.requireNonNull(loanAccount, "loanAccount must not be null");
            Objects.requireNonNull(movementId, "movementId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public String toString() {
            return "ProductActivationCommand[loanApplicationId=" + loanApplication.id()
                    + ", loanContractId=" + loanContract.id()
                    + ", loanAccountId=" + loanAccount.id()
                    + ", operationEvidence=redacted]";
        }
    }

    record CompletedActivationValidationCommand(
            LoanApplication loanApplication,
            LoanContract loanContract,
            LoanAccount loanAccount
    ) {
        public CompletedActivationValidationCommand {
            Objects.requireNonNull(loanApplication, "loanApplication must not be null");
            Objects.requireNonNull(loanContract, "loanContract must not be null");
            Objects.requireNonNull(loanAccount, "loanAccount must not be null");
        }

        @Override
        public String toString() {
            return "CompletedActivationValidationCommand[loanApplicationId="
                    + loanApplication.id()
                    + ", loanContractId=" + loanContract.id()
                    + ", loanAccountId=" + loanAccount.id()
                    + ", productEvidence=redacted]";
        }
    }
    record ProductActivationResult(
            ProductCode productCode,
            Optional<ProductExposureEffect> exposureEffect
    ) {
        public ProductActivationResult {
            Objects.requireNonNull(productCode, "productCode must not be null");
            exposureEffect = Objects.requireNonNull(
                    exposureEffect,
                    "exposureEffect must not be null"
            );
        }

        public static ProductActivationResult withoutExposureEffect(ProductCode productCode) {
            return new ProductActivationResult(productCode, Optional.empty());
        }

        public static ProductActivationResult withExposureEffect(
                ProductCode productCode,
                UUID productExposureId,
                UUID movementId,
                BigDecimal convertedAmount,
                BigDecimal resultingUsedAmount,
                BigDecimal resultingReservedAmount,
                BigDecimal resultingAvailableAmount
        ) {
            return new ProductActivationResult(
                    productCode,
                    Optional.of(new ProductExposureEffect(
                            productExposureId,
                            movementId,
                            convertedAmount,
                            resultingUsedAmount,
                            resultingReservedAmount,
                            resultingAvailableAmount
                    ))
            );
        }

        @Override
        public String toString() {
            return "ProductActivationResult[productCode=" + productCode
                    + ", productExposureEffect="
                    + (exposureEffect.isPresent() ? "present" : "absent")
                    + ", exposureAmounts=redacted]";
        }
    }

    record ProductExposureEffect(
            UUID productExposureId,
            UUID movementId,
            BigDecimal convertedAmount,
            BigDecimal resultingUsedAmount,
            BigDecimal resultingReservedAmount,
            BigDecimal resultingAvailableAmount
    ) {
        public ProductExposureEffect {
            Objects.requireNonNull(productExposureId, "productExposureId must not be null");
            Objects.requireNonNull(movementId, "movementId must not be null");
            Objects.requireNonNull(convertedAmount, "convertedAmount must not be null");
            Objects.requireNonNull(resultingUsedAmount, "resultingUsedAmount must not be null");
            Objects.requireNonNull(resultingReservedAmount, "resultingReservedAmount must not be null");
            Objects.requireNonNull(resultingAvailableAmount, "resultingAvailableAmount must not be null");
        }

        @Override
        public String toString() {
            return "ProductExposureEffect[productExposureId=" + productExposureId
                    + ", movementId=" + movementId
                    + ", exposureAmounts=redacted]";
        }
    }
}

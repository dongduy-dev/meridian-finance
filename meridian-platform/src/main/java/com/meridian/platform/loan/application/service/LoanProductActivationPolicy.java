package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface LoanProductActivationPolicy {

    ProductCode supportedProduct();

    ProductActivationResult activate(ProductActivationCommand command);

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

    record ProductActivationResult(
            ProductCode productCode,
            UUID productExposureId,
            UUID movementId,
            BigDecimal convertedAmount,
            BigDecimal resultingUsedAmount,
            BigDecimal resultingReservedAmount,
            BigDecimal resultingAvailableAmount
    ) {
        public ProductActivationResult {
            Objects.requireNonNull(productCode, "productCode must not be null");
            Objects.requireNonNull(productExposureId, "productExposureId must not be null");
            Objects.requireNonNull(movementId, "movementId must not be null");
            Objects.requireNonNull(convertedAmount, "convertedAmount must not be null");
            Objects.requireNonNull(resultingUsedAmount, "resultingUsedAmount must not be null");
            Objects.requireNonNull(resultingReservedAmount, "resultingReservedAmount must not be null");
            Objects.requireNonNull(resultingAvailableAmount, "resultingAvailableAmount must not be null");
        }

        @Override
        public String toString() {
            return "ProductActivationResult[productCode=" + productCode
                    + ", productExposureId=" + productExposureId
                    + ", movementId=" + movementId
                    + ", exposureAmounts=redacted]";
        }
    }
}

package com.meridian.platform.loan.application.port.in;

import java.util.Objects;
import java.util.UUID;

public interface RevealDisbursementDestinationUseCase {

    Result reveal(Command command);

    record Command(UUID loanApplicationId, int expectedContractVersion) {
        public Command {
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
            if (expectedContractVersion <= 0) {
                throw new IllegalArgumentException("expectedContractVersion must be positive.");
            }
        }
    }

    record Result(
            UUID loanApplicationId,
            UUID contractId,
            int contractVersion,
            String bankCode,
            String bankName,
            String accountHolderName,
            String accountNumber
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
            Objects.requireNonNull(contractId, "contractId must not be null");
            if (contractVersion <= 0) {
                throw new IllegalArgumentException("contractVersion must be positive.");
            }
            bankCode = requireText(bankCode, "bankCode");
            bankName = requireText(bankName, "bankName");
            accountHolderName = requireText(accountHolderName, "accountHolderName");
            accountNumber = requireText(accountNumber, "accountNumber");
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", contractId=" + contractId
                    + ", contractVersion=" + contractVersion
                    + ", destination=redacted]";
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank.");
            }
            return value;
        }
    }
}

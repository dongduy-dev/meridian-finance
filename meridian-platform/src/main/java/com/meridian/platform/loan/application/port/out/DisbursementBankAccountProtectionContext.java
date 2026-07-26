package com.meridian.platform.loan.application.port.out;

import java.util.Objects;
import java.util.UUID;

public record DisbursementBankAccountProtectionContext(
        UUID contractId, UUID loanApplicationId, UUID customerId, UUID sourceBankAccountId
) {
    public DisbursementBankAccountProtectionContext {
        Objects.requireNonNull(contractId);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(sourceBankAccountId);
    }
}

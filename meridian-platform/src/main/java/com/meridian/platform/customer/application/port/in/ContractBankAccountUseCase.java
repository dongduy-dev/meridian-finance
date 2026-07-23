package com.meridian.platform.customer.application.port.in;

import java.util.UUID;

public interface ContractBankAccountUseCase {
    SensitiveContractBankAccount capturePrimaryActive(UUID customerId);
    ContractBankAccountState inspectCaptured(UUID customerId, UUID bankAccountId);
    ContractBankAccountState inspectCapturedForUpdate(UUID customerId, UUID bankAccountId);

    record ContractBankAccountState(boolean customerActive, boolean accountExists, boolean accountActive) {}
}

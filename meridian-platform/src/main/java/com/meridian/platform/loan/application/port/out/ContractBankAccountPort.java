package com.meridian.platform.loan.application.port.out;

import java.util.UUID;

public interface ContractBankAccountPort {
    SensitiveDisbursementBankAccountDetails capturePrimaryActive(UUID customerId);
    ContractBankAccountState inspectCaptured(UUID customerId, UUID bankAccountId);
    ContractBankAccountState inspectCapturedForUpdate(UUID customerId, UUID bankAccountId);

    record ContractBankAccountState(boolean customerActive, boolean accountExists, boolean accountActive) {}
}

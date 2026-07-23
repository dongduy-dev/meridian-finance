package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.ContractBankAccountUseCase;
import com.meridian.platform.customer.application.port.in.SensitiveContractBankAccount;
import com.meridian.platform.loan.application.port.out.ContractBankAccountPort;
import com.meridian.platform.loan.application.port.out.SensitiveDisbursementBankAccountDetails;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Component
public class CustomerContractBankAccountAdapter implements ContractBankAccountPort {
    private final ContractBankAccountUseCase customerBankAccounts;

    public CustomerContractBankAccountAdapter(ContractBankAccountUseCase customerBankAccounts) {
        this.customerBankAccounts = customerBankAccounts;
    }

    @Override
    public SensitiveDisbursementBankAccountDetails capturePrimaryActive(UUID customerId) {
        try (SensitiveContractBankAccount account = customerBankAccounts.capturePrimaryActive(customerId)) {
            byte[] value = account.copyAccountNumber();
            try {
                return new SensitiveDisbursementBankAccountDetails(account.customerId(), account.bankAccountId(),
                        account.bankCode(), account.bankNameSnapshot(), account.accountHolderName(),
                        account.lastFour(), value);
            } finally {
                Arrays.fill(value, (byte) 0);
            }
        }
    }

    @Override
    public ContractBankAccountState inspectCaptured(UUID customerId, UUID bankAccountId) {
        ContractBankAccountUseCase.ContractBankAccountState state =
                customerBankAccounts.inspectCaptured(customerId, bankAccountId);
        return new ContractBankAccountState(state.customerActive(), state.accountExists(), state.accountActive());
    }
}

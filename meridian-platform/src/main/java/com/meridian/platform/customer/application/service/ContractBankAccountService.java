package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.ContractBankAccountUseCase;
import com.meridian.platform.customer.application.port.in.SensitiveContractBankAccount;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

@Service
public class ContractBankAccountService implements ContractBankAccountUseCase {
    private final CustomerRepository customers;
    private final CustomerSensitiveValueProtector protector;

    public ContractBankAccountService(CustomerRepository customers, CustomerSensitiveValueProtector protector) {
        this.customers = customers;
        this.protector = protector;
    }

    @Override
    @Transactional
    public SensitiveContractBankAccount capturePrimaryActive(UUID customerId) {
        Customer customer = lockCustomer(customerId);
        if (!customer.isActive()) {
            throw new BusinessStateConflictException("CUSTOMER_INACTIVE", "Customer is not active.");
        }
        CustomerBankAccount account = customer.bankAccounts().stream()
                .filter(CustomerBankAccount::isPrimaryActive)
                .findFirst()
                .orElseThrow(() -> new BusinessStateConflictException(
                        "PRIMARY_BANK_ACCOUNT_REQUIRED", "A primary active bank account is required."));
        byte[] plaintext = protector.revealToBytes(account.accountNumber());
        try {
            return new SensitiveContractBankAccount(customer.id(), account.id(), account.bankCode(),
                    account.bankNameSnapshot(), account.accountHolderName(), account.accountNumber().lastFour(), plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ContractBankAccountState inspectCaptured(UUID customerId, UUID bankAccountId) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new EntityNotFoundException(
                "CUSTOMER_NOT_FOUND", "Customer was not found."));
        return inspectCaptured(customer, bankAccountId);
    }

    @Override
    @Transactional
    public ContractBankAccountState inspectCapturedForUpdate(UUID customerId, UUID bankAccountId) {
        return inspectCaptured(lockCustomer(customerId), bankAccountId);
    }

    private ContractBankAccountState inspectCaptured(Customer customer, UUID bankAccountId) {
        return customer.bankAccounts().stream()
                .filter(account -> account.id().equals(bankAccountId))
                .findFirst()
                .map(account -> new ContractBankAccountState(customer.isActive(), true, account.isActive()))
                .orElseGet(() -> new ContractBankAccountState(customer.isActive(), false, false));
    }

    private Customer lockCustomer(UUID customerId) {
        return customers.findByIdForUpdate(customerId).orElseThrow(() -> new EntityNotFoundException(
                "CUSTOMER_NOT_FOUND", "Customer was not found."));
    }
}

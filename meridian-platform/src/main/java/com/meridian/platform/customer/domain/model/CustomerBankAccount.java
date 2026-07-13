package com.meridian.platform.customer.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerBankAccount(
        UUID id,
        UUID customerId,
        String bankCode,
        String bankNameSnapshot,
        String accountHolderName,
        ProtectedSensitiveValue accountNumber,
        CustomerBankAccountStatus status,
        boolean primaryAccount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deactivatedAt) {

    public CustomerBankAccount {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        bankCode = normalizeRequired(bankCode, "bankCode");
        bankNameSnapshot = normalizeRequired(bankNameSnapshot, "bankNameSnapshot");
        accountHolderName = normalizeRequired(accountHolderName, "accountHolderName");
        if (accountNumber == null) {
            throw new IllegalArgumentException("accountNumber is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status == CustomerBankAccountStatus.ACTIVE && deactivatedAt != null) {
            throw new IllegalArgumentException("active bank account cannot have deactivatedAt");
        }
        if (status == CustomerBankAccountStatus.DEACTIVATED && deactivatedAt == null) {
            throw new IllegalArgumentException("deactivated bank account requires deactivatedAt");
        }
        if (status != CustomerBankAccountStatus.ACTIVE && primaryAccount) {
            throw new IllegalArgumentException("only active bank accounts can be primary");
        }
    }

    public boolean isActive() {
        return status == CustomerBankAccountStatus.ACTIVE;
    }

    public boolean isPrimaryActive() {
        return isActive() && primaryAccount;
    }

    public CustomerBankAccount makePrimary(LocalDateTime now) {
        if (!isActive()) {
            throw new IllegalStateException("only active bank accounts can be primary");
        }
        return new CustomerBankAccount(
                id,
                customerId,
                bankCode,
                bankNameSnapshot,
                accountHolderName,
                accountNumber,
                status,
                true,
                createdAt,
                now,
                deactivatedAt);
    }

    public CustomerBankAccount demotePrimary(LocalDateTime now) {
        if (!primaryAccount) {
            return this;
        }
        return new CustomerBankAccount(
                id,
                customerId,
                bankCode,
                bankNameSnapshot,
                accountHolderName,
                accountNumber,
                status,
                false,
                createdAt,
                now,
                deactivatedAt);
    }

    public CustomerBankAccount deactivate(LocalDateTime now) {
        if (status == CustomerBankAccountStatus.DEACTIVATED) {
            return this;
        }
        return new CustomerBankAccount(
                id,
                customerId,
                bankCode,
                bankNameSnapshot,
                accountHolderName,
                accountNumber,
                CustomerBankAccountStatus.DEACTIVATED,
                false,
                createdAt,
                now,
                now);
    }

    @Override
    public String toString() {
        return "CustomerBankAccount[id=" + id
                + ", customerId=" + customerId
                + ", status=" + status
                + ", primaryAccount=" + primaryAccount
                + ", accountNumber=redacted]";
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
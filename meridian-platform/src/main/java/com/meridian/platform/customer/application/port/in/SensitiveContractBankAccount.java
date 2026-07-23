package com.meridian.platform.customer.application.port.in;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class SensitiveContractBankAccount implements AutoCloseable {
    private final UUID customerId;
    private final UUID bankAccountId;
    private final String bankCode;
    private final String bankNameSnapshot;
    private final String accountHolderName;
    private final String lastFour;
    private byte[] accountNumber;

    public SensitiveContractBankAccount(
            UUID customerId, UUID bankAccountId, String bankCode, String bankNameSnapshot,
            String accountHolderName, String lastFour, byte[] accountNumber
    ) {
        this.customerId = Objects.requireNonNull(customerId);
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.bankCode = Objects.requireNonNull(bankCode);
        this.bankNameSnapshot = Objects.requireNonNull(bankNameSnapshot);
        this.accountHolderName = Objects.requireNonNull(accountHolderName);
        this.lastFour = Objects.requireNonNull(lastFour);
        this.accountNumber = Objects.requireNonNull(accountNumber).clone();
    }

    public UUID customerId() { return customerId; }
    public UUID bankAccountId() { return bankAccountId; }
    public String bankCode() { return bankCode; }
    public String bankNameSnapshot() { return bankNameSnapshot; }
    public String accountHolderName() { return accountHolderName; }
    public String lastFour() { return lastFour; }

    public byte[] copyAccountNumber() {
        if (accountNumber == null) throw new IllegalStateException("Sensitive bank account value has been cleared.");
        return accountNumber.clone();
    }

    public boolean cleared() { return accountNumber == null; }

    @Override public void close() {
        if (accountNumber != null) {
            Arrays.fill(accountNumber, (byte) 0);
            accountNumber = null;
        }
    }

    @Override public String toString() {
        return "SensitiveContractBankAccount[customerId=" + customerId
                + ", bankAccountId=" + bankAccountId + ", accountNumber=redacted]";
    }
}

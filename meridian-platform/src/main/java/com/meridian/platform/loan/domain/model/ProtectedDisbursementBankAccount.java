package com.meridian.platform.loan.domain.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ProtectedDisbursementBankAccount {
    private final UUID customerId;
    private final UUID sourceBankAccountId;
    private final String bankCode;
    private final String bankNameSnapshot;
    private final String accountHolderName;
    private final String lastFour;
    private final boolean primaryAtCapture;
    private final boolean activeAtCapture;
    private final LocalDateTime capturedAt;
    private final String protectionScheme;
    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final String aadVersion;

    public ProtectedDisbursementBankAccount(
            UUID customerId, UUID sourceBankAccountId, String bankCode, String bankNameSnapshot,
            String accountHolderName, String lastFour, boolean primaryAtCapture, boolean activeAtCapture,
            LocalDateTime capturedAt, String protectionScheme, String keyId, byte[] nonce,
            byte[] ciphertext, String aadVersion
    ) {
        this.customerId = Objects.requireNonNull(customerId);
        this.sourceBankAccountId = Objects.requireNonNull(sourceBankAccountId);
        this.bankCode = requireText(bankCode);
        this.bankNameSnapshot = requireText(bankNameSnapshot);
        this.accountHolderName = requireText(accountHolderName);
        this.lastFour = requireText(lastFour);
        this.primaryAtCapture = primaryAtCapture;
        this.activeAtCapture = activeAtCapture;
        this.capturedAt = Objects.requireNonNull(capturedAt);
        this.protectionScheme = requireText(protectionScheme);
        this.keyId = requireText(keyId);
        this.nonce = Objects.requireNonNull(nonce).clone();
        this.ciphertext = Objects.requireNonNull(ciphertext).clone();
        this.aadVersion = requireText(aadVersion);
        if (!primaryAtCapture || !activeAtCapture || this.nonce.length == 0 || this.ciphertext.length == 0) {
            throw new IllegalArgumentException("Captured disbursement account must be primary, active, and protected.");
        }
    }

    public UUID customerId() { return customerId; }
    public UUID sourceBankAccountId() { return sourceBankAccountId; }
    public String bankCode() { return bankCode; }
    public String bankNameSnapshot() { return bankNameSnapshot; }
    public String accountHolderName() { return accountHolderName; }
    public String lastFour() { return lastFour; }
    public boolean primaryAtCapture() { return primaryAtCapture; }
    public boolean activeAtCapture() { return activeAtCapture; }
    public LocalDateTime capturedAt() { return capturedAt; }
    public String protectionScheme() { return protectionScheme; }
    public String keyId() { return keyId; }
    public byte[] nonce() { return nonce.clone(); }
    public byte[] ciphertext() { return ciphertext.clone(); }
    public String aadVersion() { return aadVersion; }

    @Override public String toString() {
        return "ProtectedDisbursementBankAccount[customerId=" + customerId
                + ", sourceBankAccountId=" + sourceBankAccountId + ", protectedValue=redacted]";
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProtectedDisbursementBankAccount that)) return false;
        return primaryAtCapture == that.primaryAtCapture && activeAtCapture == that.activeAtCapture
                && Objects.equals(customerId, that.customerId)
                && Objects.equals(sourceBankAccountId, that.sourceBankAccountId)
                && Objects.equals(bankCode, that.bankCode)
                && Objects.equals(bankNameSnapshot, that.bankNameSnapshot)
                && Objects.equals(accountHolderName, that.accountHolderName)
                && Objects.equals(lastFour, that.lastFour)
                && Objects.equals(capturedAt, that.capturedAt)
                && Objects.equals(protectionScheme, that.protectionScheme)
                && Objects.equals(keyId, that.keyId)
                && Arrays.equals(nonce, that.nonce) && Arrays.equals(ciphertext, that.ciphertext)
                && Objects.equals(aadVersion, that.aadVersion);
    }

    @Override public int hashCode() {
        int result = Objects.hash(customerId, sourceBankAccountId, bankCode, bankNameSnapshot,
                accountHolderName, lastFour, primaryAtCapture, activeAtCapture, capturedAt,
                protectionScheme, keyId, aadVersion);
        result = 31 * result + Arrays.hashCode(nonce);
        return 31 * result + Arrays.hashCode(ciphertext);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Protected account metadata is required.");
        return value;
    }
}

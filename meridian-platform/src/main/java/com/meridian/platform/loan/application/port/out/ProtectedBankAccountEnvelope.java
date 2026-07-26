package com.meridian.platform.loan.application.port.out;

import java.util.Arrays;
import java.util.Objects;

public final class ProtectedBankAccountEnvelope {
    private final String protectionScheme;
    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final String aadVersion;

    public ProtectedBankAccountEnvelope(String protectionScheme, String keyId, byte[] nonce, byte[] ciphertext, String aadVersion) {
        this.protectionScheme = Objects.requireNonNull(protectionScheme);
        this.keyId = Objects.requireNonNull(keyId);
        this.nonce = Objects.requireNonNull(nonce).clone();
        this.ciphertext = Objects.requireNonNull(ciphertext).clone();
        this.aadVersion = Objects.requireNonNull(aadVersion);
    }
    public String protectionScheme() { return protectionScheme; }
    public String keyId() { return keyId; }
    public byte[] nonce() { return nonce.clone(); }
    public byte[] ciphertext() { return ciphertext.clone(); }
    public String aadVersion() { return aadVersion; }
    @Override public String toString() { return "ProtectedBankAccountEnvelope[protectedValue=redacted]"; }
    @Override public boolean equals(Object other) {
        return other instanceof ProtectedBankAccountEnvelope that
                && protectionScheme.equals(that.protectionScheme) && keyId.equals(that.keyId)
                && aadVersion.equals(that.aadVersion) && Arrays.equals(nonce, that.nonce)
                && Arrays.equals(ciphertext, that.ciphertext);
    }
    @Override public int hashCode() { return Objects.hash(protectionScheme, keyId, aadVersion); }
}

package com.meridian.platform.customer.infrastructure.adapter.out.crypto;

import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AesGcmCustomerSensitiveValueProtectorTest {

    private final AesGcmCustomerSensitiveValueProtector protector =
            new AesGcmCustomerSensitiveValueProtector(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                    "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8),
                    new SecureRandom()
            );

    @Test
    void encryptsIdentityReferenceWithRandomNonceAndStableFingerprint() {
        ProtectedSensitiveValue first = protector.protectIdentityReference(" idref-mer-001 ");
        ProtectedSensitiveValue second = protector.protectIdentityReference("IDREF-MER-001");

        assertNotEquals(first.ciphertext(), second.ciphertext());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals("-001", first.lastFour());
        assertEquals("IDREF-MER-001", protector.reveal(first));
        assertFalse(first.ciphertext().contains("IDREF-MER-001"));
    }

    @Test
    void bankAccountFingerprintIncludesNormalizedBankCode() {
        ProtectedSensitiveValue first = protector.protectBankAccountNumber(" vcb ", " 1234-5678 ");
        ProtectedSensitiveValue second = protector.protectBankAccountNumber("VCB", "12345678");
        ProtectedSensitiveValue differentBank = protector.protectBankAccountNumber("ACB", "12345678");

        assertEquals(first.fingerprint(), second.fingerprint());
        assertNotEquals(first.fingerprint(), differentBank.fingerprint());
        assertEquals("5678", first.lastFour());
        assertEquals("12345678", protector.reveal(first));
        assertFalse(first.ciphertext().contains("12345678"));
    }
}

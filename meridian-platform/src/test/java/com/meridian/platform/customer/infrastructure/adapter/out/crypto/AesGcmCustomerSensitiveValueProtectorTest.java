package com.meridian.platform.customer.infrastructure.adapter.out.crypto;

import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsShortBankAccountNumberAfterNormalization() {
        assertThrows(IllegalArgumentException.class,
                () -> protector.protectBankAccountNumber("VCB", "1234"));
        assertThrows(IllegalArgumentException.class,
                () -> protector.protectBankAccountNumber("VCB", "12-34"));
        assertThrows(IllegalArgumentException.class,
                () -> protector.protectBankAccountNumber("VCB", "1 2 3 4"));
    }

    @Test
    void acceptsExactlySixNormalizedBankAccountNumberCharacters() {
        ProtectedSensitiveValue value = protector.protectBankAccountNumber("VCB", "12-3456");

        assertEquals("3456", value.lastFour());
        assertEquals("123456", protector.reveal(value));
        assertFalse(value.ciphertext().contains("123456"));
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

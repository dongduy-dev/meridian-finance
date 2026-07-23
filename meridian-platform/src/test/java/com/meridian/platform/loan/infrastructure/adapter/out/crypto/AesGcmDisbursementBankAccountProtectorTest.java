package com.meridian.platform.loan.infrastructure.adapter.out.crypto;

import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtectionContext;
import com.meridian.platform.loan.application.port.out.ProtectedBankAccountEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmDisbursementBankAccountProtectorTest {
    @Test void keyRotationPreservesHistoricalDecryptability() {
        String v1 = key(); String v2 = key();
        DisbursementBankAccountProtectionContext context = context();
        AesGcmDisbursementBankAccountProtector first = protector("v1", Map.of("v1", v1));
        ProtectedBankAccountEnvelope oldEnvelope = first.protect(value(), context);
        AesGcmDisbursementBankAccountProtector rotated = protector("v2", Map.of("v1", v1, "v2", v2));
        ProtectedBankAccountEnvelope newEnvelope = rotated.protect(value(), context);
        assertEquals("v1", oldEnvelope.keyId());
        assertEquals("v2", newEnvelope.keyId());
        assertArrayEquals(value(), rotated.revealToBytes(oldEnvelope, context));
    }

    @Test void aadAndCiphertextAreAuthenticated() {
        AesGcmDisbursementBankAccountProtector protector = protector("v1", Map.of("v1", key()));
        DisbursementBankAccountProtectionContext context = context();
        ProtectedBankAccountEnvelope envelope = protector.protect(value(), context);
        assertArrayEquals(value(), protector.revealToBytes(envelope, context));
        assertThrows(IllegalStateException.class, () -> protector.revealToBytes(envelope,
                new DisbursementBankAccountProtectionContext(UUID.randomUUID(), context.loanApplicationId(),
                        context.customerId(), context.sourceBankAccountId())));
        byte[] altered = envelope.ciphertext(); altered[0] ^= 1;
        ProtectedBankAccountEnvelope tampered = new ProtectedBankAccountEnvelope(envelope.protectionScheme(),
                envelope.keyId(), envelope.nonce(), altered, envelope.aadVersion());
        assertThrows(IllegalStateException.class, () -> protector.revealToBytes(tampered, context));
    }

    @Test void missingInvalidAndUnknownKeysFailClosedWithoutSensitiveValues() {
        DisbursementSnapshotKeyProperties missing = new DisbursementSnapshotKeyProperties();
        IllegalStateException missingError = assertThrows(IllegalStateException.class, missing::validate);
        assertFalse(missingError.getMessage().contains("account"));
        DisbursementSnapshotKeyProperties invalid = new DisbursementSnapshotKeyProperties();
        invalid.setActiveKeyId("v1"); invalid.setKeys(Map.of("v1", "invalid"));
        assertThrows(IllegalStateException.class, invalid::validate);
        AesGcmDisbursementBankAccountProtector protector = protector("v1", Map.of("v1", key()));
        ProtectedBankAccountEnvelope unknown = new ProtectedBankAccountEnvelope("AES-256-GCM", "retired",
                new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> protector.revealToBytes(unknown, context()));
        assertFalse(error.getMessage().contains("retired"));
        assertTrue(unknown.toString().contains("redacted"));
    }

    private static AesGcmDisbursementBankAccountProtector protector(String active, Map<String, String> keys) {
        DisbursementSnapshotKeyProperties properties = new DisbursementSnapshotKeyProperties();
        properties.setActiveKeyId(active); properties.setKeys(new LinkedHashMap<>(keys)); properties.validate();
        return new AesGcmDisbursementBankAccountProtector(properties, new SecureRandom());
    }
    private static String key() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return Base64.getEncoder().encodeToString(key); }
    private static byte[] value() { return "1234567890".getBytes(StandardCharsets.US_ASCII); }
    private static DisbursementBankAccountProtectionContext context() {
        return new DisbursementBankAccountProtectionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}

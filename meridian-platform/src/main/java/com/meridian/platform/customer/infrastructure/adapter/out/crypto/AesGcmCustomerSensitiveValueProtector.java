package com.meridian.platform.customer.infrastructure.adapter.out.crypto;

import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Component
public class AesGcmCustomerSensitiveValueProtector implements CustomerSensitiveValueProtector {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CIPHERTEXT_PREFIX = "v1:gcm:";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MIN_NORMALIZED_ACCOUNT_NUMBER_LENGTH = 6;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec fingerprintKey;
    private final SecureRandom secureRandom;

    @Autowired
    public AesGcmCustomerSensitiveValueProtector(
            @Value("${meridian.customer.encryption-key:}") String encryptionKey,
            @Value("${meridian.customer.fingerprint-key:}") String fingerprintKey
    ) {
        this(decodeEncryptionKey(encryptionKey), decodeFingerprintKey(fingerprintKey), new SecureRandom());
    }

    AesGcmCustomerSensitiveValueProtector(
            byte[] encryptionKey,
            byte[] fingerprintKey,
            SecureRandom secureRandom
    ) {
        this.encryptionKey = new SecretKeySpec(encryptionKey.clone(), "AES");
        this.fingerprintKey = new SecretKeySpec(fingerprintKey.clone(), HMAC_ALGORITHM);
        this.secureRandom = secureRandom;
    }

    @Override
    public ProtectedSensitiveValue protectIdentityReference(String identityReference) {
        String normalizedIdentityReference = normalizeIdentityReference(identityReference);
        return protect(normalizedIdentityReference, normalizedIdentityReference);
    }

    @Override
    public ProtectedSensitiveValue protectBankAccountNumber(String bankCode, String accountNumber) {
        String normalizedBankCode = normalizeBankCode(bankCode);
        String normalizedAccountNumber = normalizeAccountNumber(accountNumber);
        return protect(normalizedAccountNumber, normalizedBankCode + ":" + normalizedAccountNumber);
    }

    @Override
    public String reveal(ProtectedSensitiveValue protectedValue) {
        String[] envelopeParts = protectedValue.ciphertext().split(":", 4);
        if (envelopeParts.length != 4 || !protectedValue.ciphertext().startsWith(CIPHERTEXT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported customer sensitive-value envelope.");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(envelopeParts[2]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(envelopeParts[3]);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Customer sensitive-value envelope could not be decrypted.", exception);
        }
    }

    private ProtectedSensitiveValue protect(String plaintext, String fingerprintInput) {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String envelope = CIPHERTEXT_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
            return new ProtectedSensitiveValue(envelope, fingerprint(fingerprintInput), lastFour(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Customer sensitive value could not be protected.", exception);
        }
    }

    private String fingerprint(String normalizedInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(fingerprintKey);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(normalizedInput.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Customer sensitive value fingerprint could not be created.", exception);
        }
    }

    private static String normalizeIdentityReference(String value) {
        return requireText(value, "identityReference").toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String normalizeBankCode(String value) {
        return requireText(value, "bankCode").toUpperCase(Locale.ROOT);
    }

    private static String normalizeAccountNumber(String value) {
        String normalized = requireText(value, "accountNumber").replaceAll("[\\s-]+", "");
        if (normalized.length() < MIN_NORMALIZED_ACCOUNT_NUMBER_LENGTH) {
            throw new IllegalArgumentException("accountNumber must contain at least 6 characters after normalization");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String lastFour(String normalizedValue) {
        return normalizedValue.substring(Math.max(0, normalizedValue.length() - 4));
    }

    private static byte[] decodeEncryptionKey(String encodedKey) {
        byte[] key = decodeBase64Key(encodedKey, "MERIDIAN_CUSTOMER_ENCRYPTION_KEY");
        if (key.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("MERIDIAN_CUSTOMER_ENCRYPTION_KEY must be a Base64-encoded 32-byte AES key.");
        }
        return key;
    }

    private static byte[] decodeFingerprintKey(String encodedKey) {
        byte[] key = decodeBase64Key(encodedKey, "MERIDIAN_CUSTOMER_FINGERPRINT_KEY");
        if (key.length < AES_256_KEY_BYTES) {
            throw new IllegalStateException("MERIDIAN_CUSTOMER_FINGERPRINT_KEY must be a Base64-encoded key of at least 32 bytes.");
        }
        return key;
    }

    private static byte[] decodeBase64Key(String encodedKey, String envName) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(envName + " is required.");
        }
        try {
            return Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(envName + " must be Base64-encoded.", exception);
        }
    }
}

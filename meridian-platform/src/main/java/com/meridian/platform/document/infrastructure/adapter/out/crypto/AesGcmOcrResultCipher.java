package com.meridian.platform.document.infrastructure.adapter.out.crypto;

import com.meridian.platform.document.application.port.out.OcrResultCipher;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmOcrResultCipher implements OcrResultCipher {

    private static final String PREFIX = "v1:gcm:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmOcrResultCipher(
            @Value("${meridian.document.ocr-result-encryption-key:}") String encodedKey
    ) {
        this.key = decodeKey(encodedKey);
    }

    @Override
    public String encrypt(String plaintext) {
        requireConfigured();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + encode(nonce) + ":" + encode(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String decrypt(String envelope) {
        requireConfigured();
        String[] parts = envelope.split(":", -1);
        if (parts.length != 4 || !"v1".equals(parts[0]) || !"gcm".equals(parts[1])) {
            throw unavailable(null);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, decode(parts[2])));
            return new String(cipher.doFinal(decode(parts[3])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private void requireConfigured() {
        if (key == null) throw unavailable(null);
    }

    private static SecretKeySpec decodeKey(String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey);
            return decoded.length == 32 ? new SecretKeySpec(decoded, "AES") : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static ServiceUnavailableException unavailable(Exception cause) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
                "OCR_REVIEW_UNAVAILABLE", "OCR review is temporarily unavailable."
        );
        if (cause != null) exception.initCause(cause);
        return exception;
    }
}

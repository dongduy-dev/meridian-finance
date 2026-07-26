package com.meridian.platform.loan.infrastructure.adapter.out.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "meridian.loan.disbursement-snapshot")
public class DisbursementSnapshotKeyProperties {
    private static final int AES_256_KEY_BYTES = 32;
    private String activeKeyId;
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId; }
    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys); }

    @PostConstruct
    void validate() {
        if (activeKeyId == null || activeKeyId.isBlank()) fail();
        if (!keys.containsKey(activeKeyId)) fail();
        keys.forEach((keyId, encoded) -> {
            if (keyId == null || keyId.isBlank() || decode(encoded).length != AES_256_KEY_BYTES) fail();
        });
    }

    byte[] resolve(String keyId) {
        String encoded = keys.get(keyId);
        if (encoded == null) {
            throw new IllegalStateException("Disbursement snapshot key is unavailable.");
        }
        byte[] key = decode(encoded);
        if (key.length != AES_256_KEY_BYTES) fail();
        return key;
    }

    private static byte[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) fail();
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Disbursement snapshot key configuration is invalid.");
        }
    }

    private static void fail() {
        throw new IllegalStateException("Disbursement snapshot key configuration is invalid.");
    }
}

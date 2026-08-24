package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaqueRefreshTokenServiceTest {

    @Test
    void generatesAtLeast256BitsAndStoresOnlyDeterministicSha256DigestMaterial() {
        OpaqueRefreshTokenService service = new OpaqueRefreshTokenService();

        GeneratedRefreshToken first = service.generate();
        GeneratedRefreshToken second = service.generate();

        assertEquals(43, first.tokenValue().length());
        assertEquals(64, first.tokenDigest().length());
        assertTrue(first.tokenDigest().matches("[0-9a-f]{64}"));
        assertEquals(first.tokenDigest(), service.digest(first.tokenValue()));
        assertNotEquals(first.tokenValue(), first.tokenDigest());
        assertNotEquals(first.tokenValue(), second.tokenValue());
        assertNotEquals(first.tokenDigest(), second.tokenDigest());
    }
}

package com.meridian.platform.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaquePasswordResetTokenServiceTest {

    @Test
    void generatesIndependent256BitTokensAndPersistsSha256CompatibleDigests() {
        OpaquePasswordResetTokenService service = new OpaquePasswordResetTokenService();

        var first = service.generate();
        var second = service.generate();

        assertNotEquals(first.tokenValue(), second.tokenValue());
        assertEquals(43, first.tokenValue().length());
        assertEquals(64, first.tokenDigest().length());
        assertTrue(first.tokenDigest().matches("[0-9a-f]{64}"));
        assertEquals(first.tokenDigest(), service.digest(first.tokenValue()));
        assertNotEquals(first.tokenValue(), first.tokenDigest());
    }
}

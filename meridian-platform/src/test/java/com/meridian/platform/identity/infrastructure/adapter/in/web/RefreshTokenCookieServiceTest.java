package com.meridian.platform.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieServiceTest {

    @Test
    void createsNarrowHttpOnlyStrictSecureCookieWithAlignedExpiry() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(true);
        Instant issuedAt = Instant.parse("2026-08-24T00:00:00Z");

        String header = service.issue("raw-token", issuedAt, issuedAt.plusSeconds(604800));

        assertTrue(header.contains("MERIDIAN_REFRESH_TOKEN=raw-token"));
        assertTrue(header.contains("Path=/api/v1/auth/refresh"));
        assertTrue(header.contains("Max-Age=604800"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
    }

    @Test
    void readsExactlyOneConfiguredCookie() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "ignored"), new Cookie("MERIDIAN_REFRESH_TOKEN", "raw-token"));

        assertEquals("raw-token", service.read(request).orElseThrow());

        request.setCookies(
                new Cookie("MERIDIAN_REFRESH_TOKEN", "first"),
                new Cookie("MERIDIAN_REFRESH_TOKEN", "second")
        );
        assertTrue(service.read(request).isEmpty());
    }
}

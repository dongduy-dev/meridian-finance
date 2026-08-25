package com.meridian.platform.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class RefreshTokenCookieService {

    static final String COOKIE_PATH = "/api/v1/auth/refresh";
    static final String COOKIE_NAME = "MERIDIAN_REFRESH_TOKEN";

    private final boolean secure;

    public RefreshTokenCookieService(
            @Value("${meridian.identity.refresh-token.cookie-secure:false}") boolean secure
    ) {
        this.secure = secure;
    }

    public Optional<String> read(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        String value = null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                if (value != null) {
                    return Optional.empty();
                }
                value = cookie.getValue();
            }
        }
        return Optional.ofNullable(value).filter(token -> !token.isBlank());
    }

    public String issue(String token, Instant issuedAt, Instant expiresAt) {
        Duration maxAge = Duration.between(issuedAt, expiresAt);
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("refresh-token cookie expiry must be after issuance");
        }
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}

package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class OpaqueRefreshTokenService implements RefreshTokenCodecPort {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom secureRandom;

    public OpaqueRefreshTokenService() {
        this(new SecureRandom());
    }

    OpaqueRefreshTokenService(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public GeneratedRefreshToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = TOKEN_ENCODER.encodeToString(randomBytes);
        return new GeneratedRefreshToken(rawToken, digest(rawToken));
    }

    @Override
    public String digest(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}

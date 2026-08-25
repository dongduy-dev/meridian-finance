package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.GeneratedEmailVerificationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class OpaqueEmailVerificationTokenService implements EmailVerificationTokenCodecPort {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom secureRandom;

    public OpaqueEmailVerificationTokenService() {
        this(new SecureRandom());
    }

    OpaqueEmailVerificationTokenService(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public GeneratedEmailVerificationToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = TOKEN_ENCODER.encodeToString(randomBytes);
        return new GeneratedEmailVerificationToken(rawToken, digest(rawToken));
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

package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.TokenIssuerPort;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtTokenService implements TokenIssuerPort {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final JwtKeyProvider keyProvider;
    private final Clock clock;

    @Autowired
    public JwtTokenService(JwtKeyProvider keyProvider) {
        this(keyProvider, Clock.systemUTC());
    }

    JwtTokenService(JwtKeyProvider keyProvider, Clock clock) {
        this.keyProvider = keyProvider;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issueAccessToken(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        String token = createToken(user, issuedAt, expiresAt);
        return new IssuedAccessToken(token, expiresAt);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalidToken();
        }

        String signingInput = parts[0] + "." + parts[1];
        verifySignature(signingInput, parts[2]);

        String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        Instant expiresAt = Instant.ofEpochSecond(extractLong(payload, "exp"));
        if (!Instant.now(clock).isBefore(expiresAt)) {
            throw new JwtAuthenticationException("TOKEN_EXPIRED", "Token expired.");
        }

        return new AuthenticatedUser(
                UUID.fromString(extractString(payload, "sub")),
                extractString(payload, "email"),
                extractString(payload, "userType"),
                extractOptionalString(payload, "customerId").map(UUID::fromString).orElse(null),
                extractStringArray(payload, "roles"),
                extractStringArray(payload, "permissions")
        );
    }

    private String createToken(User user, Instant issuedAt, Instant expiresAt) {
        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":\"" + escapeJson(user.id().toString()) + "\","
                + "\"email\":\"" + escapeJson(user.email()) + "\","
                + "\"userType\":\"" + escapeJson(user.userType().name()) + "\","
                + "\"customerId\":" + nullableJsonString(user.customerId()) + ","
                + "\"roles\":" + stringArrayJson(user.roles()) + ","
                + "\"permissions\":" + stringArrayJson(user.permissions()) + ","
                + "\"iat\":" + issuedAt.getEpochSecond() + ","
                + "\"exp\":" + expiresAt.getEpochSecond()
                + "}";

        String signingInput = base64Url(headerJson) + "." + base64Url(payloadJson);
        return signingInput + "." + sign(signingInput);
    }

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyProvider.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL_ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign access token.", exception);
        }
    }

    private void verifySignature(String signingInput, String signatureValue) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(keyProvider.publicKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(BASE64_URL_DECODER.decode(signatureValue))) {
                throw invalidToken();
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw invalidToken();
        }
    }

    private String base64Url(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String nullableJsonString(UUID value) {
        return value == null ? "null" : "\"" + escapeJson(value.toString()) + "\"";
    }

    private String stringArrayJson(Set<String> values) {
        return new TreeSet<>(values).stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .reduce((first, second) -> first + "," + second)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String extractString(String payload, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw invalidToken();
        }
        return unescapeJson(matcher.group(1));
    }

    private Optional<String> extractOptionalString(String payload, String fieldName) {
        Pattern stringPattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher stringMatcher = stringPattern.matcher(payload);
        if (stringMatcher.find()) {
            return Optional.of(unescapeJson(stringMatcher.group(1)));
        }

        Pattern nullPattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*null");
        if (nullPattern.matcher(payload).find()) {
            return Optional.empty();
        }
        throw invalidToken();
    }

    private long extractLong(String payload, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw invalidToken();
        }
        return Long.parseLong(matcher.group(1));
    }

    private Set<String> extractStringArray(String payload, String fieldName) {
        Pattern arrayPattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\[(.*?)]");
        Matcher arrayMatcher = arrayPattern.matcher(payload);
        if (!arrayMatcher.find()) {
            throw invalidToken();
        }

        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(arrayMatcher.group(1));
        while (valueMatcher.find()) {
            values.add(unescapeJson(valueMatcher.group(1)));
        }
        return values;
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private JwtAuthenticationException invalidToken() {
        return new JwtAuthenticationException("INVALID_TOKEN", "Invalid token.");
    }
}

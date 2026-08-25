package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    void issuesAndParsesAccessToken() {
        JwtKeyProvider keyProvider = new JwtKeyProvider(generateKeyPair());
        JwtTokenService tokenService = new JwtTokenService(
                keyProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        String token = tokenService.issueAccessToken(customerUser()).tokenValue();
        AuthenticatedUser authenticatedUser = tokenService.parseAccessToken(token);

        assertEquals(USER_ID, authenticatedUser.userId());
        assertEquals("customer.demo@meridian.local", authenticatedUser.email());
        assertEquals("CUSTOMER", authenticatedUser.userType());
        assertEquals(CUSTOMER_ID, authenticatedUser.customerId());
        assertEquals(Set.of("CUSTOMER"), authenticatedUser.roles());
        assertEquals(Set.of("loan:submit", "partner:employee:verify:own"), authenticatedUser.permissions());
    }

    @Test
    void rejectsExpiredToken() {
        JwtKeyProvider keyProvider = new JwtKeyProvider(generateKeyPair());
        JwtTokenService issuer = new JwtTokenService(
                keyProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        JwtTokenService parserAfterExpiry = new JwtTokenService(
                keyProvider,
                Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC)
        );

        String token = issuer.issueAccessToken(customerUser()).tokenValue();

        JwtAuthenticationException exception = assertThrows(
                JwtAuthenticationException.class,
                () -> parserAfterExpiry.parseAccessToken(token)
        );

        assertEquals("TOKEN_EXPIRED", exception.getErrorCode());
    }

    @Test
    void rejectsMalformedToken() {
        JwtTokenService tokenService = new JwtTokenService(
                new JwtKeyProvider(generateKeyPair()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        JwtAuthenticationException exception = assertThrows(
                JwtAuthenticationException.class,
                () -> tokenService.parseAccessToken("not-a-jwt")
        );

        assertEquals("INVALID_TOKEN", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid token"));
    }

    @Test
    void configuredKeysRemainValidAcrossServiceRecreation() {
        KeyPair keyPair = generateKeyPair();
        JwtTokenService firstInstance = tokenService(configuredProvider(keyPair));
        String token = firstInstance.issueAccessToken(customerUser()).tokenValue();

        JwtTokenService recreatedInstance = tokenService(configuredProvider(keyPair));

        assertEquals(USER_ID, recreatedInstance.parseAccessToken(token).userId());
    }

    @Test
    void differentSigningMaterialRejectsExistingToken() {
        String token = tokenService(generateKeyPair()).issueAccessToken(customerUser()).tokenValue();
        JwtTokenService differentInstance = tokenService(generateKeyPair());

        JwtAuthenticationException exception = assertThrows(
                JwtAuthenticationException.class,
                () -> differentInstance.parseAccessToken(token)
        );

        assertEquals("INVALID_TOKEN", exception.getErrorCode());
    }

    @Test
    void loadsBase64Pkcs8AndX509Configuration() {
        KeyPair keyPair = generateKeyPair();
        JwtKeyProvider provider = new JwtKeyProvider(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
        );

        assertEquals(keyPair.getPrivate(), provider.privateKey());
        assertEquals(keyPair.getPublic(), provider.publicKey());
    }

    @Test
    void rejectsMissingMalformedAndMismatchedSigningConfiguration() {
        KeyPair first = generateKeyPair();
        KeyPair second = generateKeyPair();

        assertThrows(IllegalStateException.class, () -> new JwtKeyProvider("", ""));
        assertThrows(IllegalStateException.class, () -> new JwtKeyProvider("not-base64", "not-base64"));
        assertThrows(IllegalStateException.class, () -> new JwtKeyProvider(
                Base64.getEncoder().encodeToString(first.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(second.getPublic().getEncoded())
        ));
    }

    private JwtTokenService tokenService(KeyPair keyPair) {
        return tokenService(new JwtKeyProvider(keyPair));
    }

    private JwtTokenService tokenService(JwtKeyProvider keyProvider) {
        return new JwtTokenService(
                keyProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private JwtKeyProvider configuredProvider(KeyPair keyPair) {
        return new JwtKeyProvider(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
        );
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private User customerUser() {
        return new User(
                USER_ID,
                "customer.demo@meridian.local",
                "hash",
                UserType.CUSTOMER,
                UserStatus.ACTIVE,
                "Customer Demo",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                Set.of("loan:submit", "partner:employee:verify:own")
        );
    }
}

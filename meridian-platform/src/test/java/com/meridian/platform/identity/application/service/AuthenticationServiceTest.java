package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.PasswordVerifierPort;
import com.meridian.platform.identity.application.port.out.TokenIssuerPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    void returnsBearerTokenForActiveUserWithValidPassword() {
        AuthenticationService service = new AuthenticationService(
                normalizedEmail -> Optional.of(customerUser(UserStatus.ACTIVE)),
                (rawPassword, passwordHash) -> rawPassword.equals("valid-password"),
                user -> new IssuedAccessToken("token-value", Instant.parse("2026-06-29T00:00:00Z"))
        );

        AuthResponse response = service.login(new LoginRequest(
                " Customer.Demo@Meridian.Local ",
                "valid-password"
        ));

        assertEquals("Bearer", response.tokenType());
        assertEquals("token-value", response.accessToken());
        assertEquals(USER_ID, response.userId());
        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(Set.of("CUSTOMER"), response.roles());
        assertEquals(Set.of("loan:submit"), response.permissions());
    }

    @Test
    void rejectsInvalidCredentials() {
        AuthenticationService service = new AuthenticationService(
                normalizedEmail -> Optional.of(customerUser(UserStatus.ACTIVE)),
                (rawPassword, passwordHash) -> false,
                unusedTokenIssuer()
        );

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("customer.demo@meridian.local", "wrong-password"))
        );

        assertEquals("INVALID_CREDENTIALS", exception.getErrorCode());
    }

    @Test
    void rejectsInactiveUser() {
        AuthenticationService service = new AuthenticationService(
                normalizedEmail -> Optional.of(customerUser(UserStatus.SUSPENDED)),
                (rawPassword, passwordHash) -> true,
                unusedTokenIssuer()
        );

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("customer.demo@meridian.local", "irrelevant"))
        );

        assertEquals("ACCOUNT_SUSPENDED", exception.getErrorCode());
    }

    private User customerUser(UserStatus status) {
        return new User(
                USER_ID,
                "customer.demo@meridian.local",
                "hash",
                UserType.CUSTOMER,
                status,
                "Customer Demo",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                Set.of("loan:submit")
        );
    }

    private TokenIssuerPort unusedTokenIssuer() {
        return user -> {
            throw new AssertionError("Token issuer should not be called.");
        };
    }
}


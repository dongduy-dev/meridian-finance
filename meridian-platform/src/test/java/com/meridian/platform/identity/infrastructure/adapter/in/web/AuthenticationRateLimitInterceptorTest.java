package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.LogoutUseCase;
import com.meridian.platform.identity.infrastructure.security.JwtTokenService;
import com.meridian.platform.identity.infrastructure.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthenticationRateLimitInterceptorTest {

    private AuthenticationUseCase authenticationUseCase;
    private LogoutUseCase logoutUseCase;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        authenticationUseCase = mock(AuthenticationUseCase.class);
        logoutUseCase = mock(LogoutUseCase.class);
        clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        when(authenticationUseCase.login(any())).thenReturn(authenticationResult("login-access", "login-refresh"));
        when(authenticationUseCase.refresh(any())).thenReturn(authenticationResult("new-access", "new-refresh"));
    }

    @Test
    void allowsLoginBelowThresholdAndRejectsTheFirstRequestOverItWithRetryAfter() throws Exception {
        MockMvc mockMvc = mockMvc(2, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        String retryAfter = mockMvc.perform(login("192.0.2.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests."))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.RETRY_AFTER);

        assertTrue(Long.parseLong(retryAfter) > 0);
        verify(authenticationUseCase, times(2)).login(any());
    }

    @Test
    void permitsLoginAgainWhenTheFixedWindowExpiresWithoutSleeping() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());

        verify(authenticationUseCase, times(2)).login(any());
    }

    @Test
    void keepsLoginAndRefreshPoliciesIndependent() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 2, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isTooManyRequests());
        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isTooManyRequests());

        verify(authenticationUseCase).login(any());
        verify(authenticationUseCase, times(2)).refresh("valid-refresh-token");
    }

    @Test
    void keepsRemoteAddressCapacityIndependent() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("198.51.100.2")).andExpect(status().isOk());

        verify(authenticationUseCase, times(2)).login(any());
    }

    @Test
    void ignoresCallerSuppliedForwardingHeaders() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1").header("X-Forwarded-For", "198.51.100.10"))
                .andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1").header("X-Forwarded-For", "203.0.113.20"))
                .andExpect(status().isTooManyRequests());

        verify(authenticationUseCase).login(any());
    }

    @Test
    void optionsPreflightDoesNotConsumeLoginCapacity() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(options(AuthenticationRateLimitInterceptor.LOGIN_PATH)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.1");
                            return request;
                        }))
                .andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isTooManyRequests());

        verify(authenticationUseCase).login(any());
    }

    @Test
    void logoutIsNeverRateLimited() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.1");
                                return request;
                            }))
                    .andExpect(status().isNoContent());
        }

        verify(logoutUseCase, times(5)).logout(any());
    }

    @Test
    void rejectedLoginNeverReachesAccountLockoutStateMutation() throws Exception {
        MockMvc mockMvc = mockMvc(1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1));

        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isTooManyRequests());

        verify(authenticationUseCase).login(any());
    }

    @Test
    void rejectedRefreshNeverConsumesOrRotatesRefreshState() throws Exception {
        MockMvc mockMvc = mockMvc(3, Duration.ofMinutes(1), 1, Duration.ofMinutes(1));

        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isTooManyRequests());

        verify(authenticationUseCase).refresh("valid-refresh-token");
    }

    @Test
    void rejectsInvalidConfiguredPoliciesDuringConstruction() {
        SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationRateLimitInterceptor(writer, 0, Duration.ofMinutes(1), 1, Duration.ofMinutes(1))
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationRateLimitInterceptor(writer, 1, Duration.ZERO, 1, Duration.ofMinutes(1))
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationRateLimitInterceptor(writer, 1, Duration.ofMinutes(1), -1, Duration.ofMinutes(1))
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationRateLimitInterceptor(writer, 1, Duration.ofMinutes(1), 1, Duration.ofSeconds(-1))
        );
    }

    private MockMvc mockMvc(
            int loginMaxRequests,
            Duration loginWindow,
            int refreshMaxRequests,
            Duration refreshWindow
    ) {
        AuthController controller = new AuthController(
                authenticationUseCase,
                logoutUseCase,
                new RefreshTokenCookieService(false),
                mock(JwtTokenService.class)
        );
        AuthenticationRateLimitInterceptor interceptor = new AuthenticationRateLimitInterceptor(
                new SecurityErrorResponseWriter(),
                clock,
                loginMaxRequests,
                loginWindow,
                refreshMaxRequests,
                refreshWindow,
                100
        );
        return standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String remoteAddress) {
        return post(AuthenticationRateLimitInterceptor.LOGIN_PATH)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "customer.demo@meridian.local",
                          "password": "local-demo-password"
                        }
                        """);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String remoteAddress) {
        return post(AuthenticationRateLimitInterceptor.REFRESH_PATH)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .cookie(new jakarta.servlet.http.Cookie("MERIDIAN_REFRESH_TOKEN", "valid-refresh-token"));
    }

    private static AuthenticationResult authenticationResult(String accessToken, String refreshToken) {
        Instant issuedAt = Instant.parse("2026-08-25T00:00:00Z");
        return new AuthenticationResult(
                new AuthResponse(
                        "Bearer",
                        accessToken,
                        issuedAt.plusSeconds(3600),
                        UUID.fromString("00000000-0000-0000-0000-000000000301"),
                        "customer.demo@meridian.local",
                        "CUSTOMER",
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        Set.of("CUSTOMER"),
                        Set.of("loan:submit")
                ),
                refreshToken,
                issuedAt,
                issuedAt.plusSeconds(604800)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported in this test Clock.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

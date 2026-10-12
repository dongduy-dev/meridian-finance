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
    private com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase registerCustomerUseCase;
    private com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase requestEmailVerificationUseCase;
    private com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase requestPasswordResetUseCase;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        authenticationUseCase = mock(AuthenticationUseCase.class);
        logoutUseCase = mock(LogoutUseCase.class);
        registerCustomerUseCase = mock(com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase.class);
        requestEmailVerificationUseCase = mock(
                com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase.class
        );
        requestPasswordResetUseCase = mock(
                com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase.class
        );
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

        mockMvc.perform(login("192.0.2.1").header("X-Forwarded-For", "198.51.100.10")
                        .header("CF-Connecting-IP", "198.51.100.10").header("Forwarded", "for=198.51.100.10"))
                .andExpect(status().isOk());
        mockMvc.perform(login("192.0.2.1").header("X-Forwarded-For", "203.0.113.20")
                        .header("CF-Connecting-IP", "203.0.113.20").header("Forwarded", "for=203.0.113.20"))
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
    void registrationAndVerificationRequestHaveIndependentPoliciesAndIgnoreForwardingHeaders() throws Exception {
        MockMvc mockMvc = mockMvc(10, Duration.ofMinutes(1), 10, Duration.ofMinutes(1), 1, 1);

        mockMvc.perform(registration("192.0.2.1", "198.51.100.10"))
                .andExpect(status().isCreated());
        mockMvc.perform(registration("192.0.2.1", "203.0.113.20"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(verificationRequest("192.0.2.1", "198.51.100.30"))
                .andExpect(status().isAccepted());
        mockMvc.perform(verificationRequest("192.0.2.1", "203.0.113.40"))
                .andExpect(status().isTooManyRequests());

        verify(registerCustomerUseCase).register(any());
        verify(requestEmailVerificationUseCase).requestVerification(any());
    }

    @Test
    void passwordResetRequestHasAnIndependentPolicyAndIgnoresForwardingHeaders() throws Exception {
        MockMvc mockMvc = mockMvc(10, Duration.ofMinutes(1), 10, Duration.ofMinutes(1), 10, 10, 1);

        mockMvc.perform(passwordResetRequest("192.0.2.1", "198.51.100.10"))
                .andExpect(status().isAccepted());
        mockMvc.perform(passwordResetRequest("192.0.2.1", "203.0.113.20"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
        mockMvc.perform(login("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(refresh("192.0.2.1")).andExpect(status().isOk());
        mockMvc.perform(registration("192.0.2.1", "198.51.100.30")).andExpect(status().isCreated());
        mockMvc.perform(verificationRequest("192.0.2.1", "198.51.100.40")).andExpect(status().isAccepted());

        verify(requestPasswordResetUseCase).requestReset(any());
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
        return mockMvc(
                loginMaxRequests,
                loginWindow,
                refreshMaxRequests,
                refreshWindow,
                5,
                5
        );
    }

    private MockMvc mockMvc(
            int loginMaxRequests,
            Duration loginWindow,
            int refreshMaxRequests,
            Duration refreshWindow,
            int registrationMaxRequests,
            int verificationRequestMaxRequests
    ) {
        return mockMvc(
                loginMaxRequests,
                loginWindow,
                refreshMaxRequests,
                refreshWindow,
                registrationMaxRequests,
                verificationRequestMaxRequests,
                5
        );
    }

    private MockMvc mockMvc(
            int loginMaxRequests,
            Duration loginWindow,
            int refreshMaxRequests,
            Duration refreshWindow,
            int registrationMaxRequests,
            int verificationRequestMaxRequests,
            int passwordResetRequestMaxRequests
    ) {
        return mockMvc(loginMaxRequests, loginWindow, refreshMaxRequests, refreshWindow,
                registrationMaxRequests, verificationRequestMaxRequests, passwordResetRequestMaxRequests,
                AuthenticationClientAddressResolver.Mode.REMOTE_ADDR);
    }

    private MockMvc cloudflareMockMvc() {
        return mockMvc(1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1), 1, 1, 1,
                AuthenticationClientAddressResolver.Mode.CLOUDFLARE);
    }

    @Test
    void cloudflareClientsHaveSeparateBucketsButGenericForwardingCannotChangeIdentity() throws Exception {
        MockMvc mvc = cloudflareMockMvc();
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "198.51.100.1"))
                .andExpect(status().isOk());
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "198.51.100.2"))
                .andExpect(status().isOk());
        mvc.perform(login("172.20.0.3").header("CF-Connecting-IP", "198.51.100.1")
                        .header("X-Forwarded-For", "203.0.113.10").header("Forwarded", "for=203.0.113.10"))
                .andExpect(status().isTooManyRequests());
        verify(authenticationUseCase, times(2)).login(any());
    }

    @Test
    void equivalentIpv6LiteralsShareOneBucket() throws Exception {
        MockMvc mvc = cloudflareMockMvc();
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "2001:db8::a"))
                .andExpect(status().isOk());
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "2001:0DB8:0:0:0:0:0:000a"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void missingOrInvalidCloudflareMetadataFailsBeforeAuthenticationWithoutConsumingCapacity() throws Exception {
        MockMvc mvc = cloudflareMockMvc();
        mvc.perform(login("172.20.0.2")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "private.invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid client address metadata."));
        mvc.perform(refresh("172.20.0.2")).andExpect(status().isBadRequest());
        mvc.perform(registration("172.20.0.2", "198.51.100.1")).andExpect(status().isBadRequest());
        mvc.perform(verificationRequest("172.20.0.2", "198.51.100.1")).andExpect(status().isBadRequest());
        mvc.perform(passwordResetRequest("172.20.0.2", "198.51.100.1")).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(authenticationUseCase, registerCustomerUseCase,
                requestEmailVerificationUseCase, requestPasswordResetUseCase);
        mvc.perform(login("172.20.0.2").header("CF-Connecting-IP", "198.51.100.1"))
                .andExpect(status().isOk());
    }

    @Test
    void cloudflareModeKeepsAllFivePoliciesIndependentAndLeavesPreflightAndLogoutAlone() throws Exception {
        MockMvc mvc = cloudflareMockMvc();
        mvc.perform(options(AuthenticationRateLimitInterceptor.LOGIN_PATH)).andExpect(status().isOk());
        var requests = java.util.List.of(login("172.20.0.2"), refresh("172.20.0.2"),
                registration("172.20.0.2", "203.0.113.1"), verificationRequest("172.20.0.2", "203.0.113.1"),
                passwordResetRequest("172.20.0.2", "203.0.113.1"));
        for (var request : requests) {
            request.header("CF-Connecting-IP", "198.51.100.1");
            mvc.perform(request).andExpect(status().is2xxSuccessful());
            mvc.perform(request).andExpect(status().isTooManyRequests());
        }
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    private MockMvc mockMvc(
            int loginMaxRequests, Duration loginWindow, int refreshMaxRequests, Duration refreshWindow,
            int registrationMaxRequests, int verificationRequestMaxRequests, int passwordResetRequestMaxRequests,
            AuthenticationClientAddressResolver.Mode mode
    ) {
        AuthController controller = new AuthController(
                authenticationUseCase,
                logoutUseCase,
                registerCustomerUseCase,
                requestEmailVerificationUseCase,
                mock(com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase.class),
                requestPasswordResetUseCase,
                mock(com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase.class),
                new RefreshTokenCookieService(false),
                mock(JwtTokenService.class)
        );
        AuthenticationRateLimitInterceptor interceptor = new AuthenticationRateLimitInterceptor(
                new SecurityErrorResponseWriter(),
                new AuthenticationClientAddressResolver(mode),
                clock,
                loginMaxRequests,
                loginWindow,
                refreshMaxRequests,
                refreshWindow,
                registrationMaxRequests,
                Duration.ofMinutes(10),
                verificationRequestMaxRequests,
                Duration.ofMinutes(10),
                passwordResetRequestMaxRequests,
                Duration.ofMinutes(10),
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

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registration(
            String remoteAddress,
            String forwardedFor
    ) {
        return post(AuthenticationRateLimitInterceptor.REGISTRATION_PATH)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "customer@example.com",
                          "password": "registration-password",
                          "displayName": "Customer Name"
                        }
                        """);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verificationRequest(
            String remoteAddress,
            String forwardedFor
    ) {
        return post(AuthenticationRateLimitInterceptor.EMAIL_VERIFICATION_REQUEST_PATH)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "customer@example.com"}
                        """);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder passwordResetRequest(
            String remoteAddress,
            String forwardedFor
    ) {
        return post(AuthenticationRateLimitInterceptor.PASSWORD_RESET_REQUEST_PATH)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "customer@example.com"}
                        """);
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

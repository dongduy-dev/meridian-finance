package com.meridian.platform.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentCorsTest {
    private final CorsFilter filter = new CorsFilter(new SecurityConfig().corsConfigurationSource(List.of(
            "https://meridianfinance.dev", "https://internal.meridianfinance.dev")));

    @ParameterizedTest
    @ValueSource(strings = {"https://meridianfinance.dev", "https://internal.meridianfinance.dev"})
    void permitsCredentialedPreflightOnlyForExactProductionOrigins(String origin) throws Exception {
        var response = preflight(origin);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "http://localhost:5173", "http://localhost:5174",
            "http://meridianfinance.dev", "https://preview.meridianfinance.dev", "https://meridianfinance.dev.attacker.example",
            "https://meridianfinance.dev:8443", "null"})
    void rejectsUnrelatedLocalAndLookalikeOrigins(String origin) throws Exception {
        var response = preflight(origin);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void wildcardCannotBeConfigured() {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletResponse preflight(String origin) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }
}

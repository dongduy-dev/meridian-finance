package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.infrastructure.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationClientAddressConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withBean(SecurityErrorResponseWriter.class)
            .withBean(AuthenticationRateLimitInterceptor.class);

    @Test
    void defaultsToRemoteAddress() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var request = new MockHttpServletRequest("POST", AuthenticationRateLimitInterceptor.LOGIN_PATH);
            assertThat(context.getBean(AuthenticationRateLimitInterceptor.class)
                    .preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        });
    }

    @Test
    void cloudflareModeRequiresMetadata() {
        runner.withPropertyValues("meridian.identity.rate-limit.client-address-mode=CLOUDFLARE").run(context -> {
            assertThat(context).hasNotFailed();
            var request = new MockHttpServletRequest("POST", AuthenticationRateLimitInterceptor.LOGIN_PATH);
            var response = new MockHttpServletResponse();
            assertThat(context.getBean(AuthenticationRateLimitInterceptor.class)
                    .preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(400);
        });
    }

    @Test
    void unknownModeFailsStartupInsteadOfFallingBack() {
        runner.withPropertyValues("meridian.identity.rate-limit.client-address-mode=TRUST_ALL")
                .run(context -> assertThat(context).hasFailed());
    }
}

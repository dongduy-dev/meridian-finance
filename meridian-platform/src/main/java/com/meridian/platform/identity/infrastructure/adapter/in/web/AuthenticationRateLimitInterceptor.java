package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.infrastructure.security.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;

@Component
public class AuthenticationRateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String REFRESH_PATH = "/api/v1/auth/refresh";
    static final int MAX_TRACKED_CLIENTS_PER_ENDPOINT = 10_000;

    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";
    private static final String ERROR_MESSAGE = "Too many requests.";

    private final SecurityErrorResponseWriter errorResponseWriter;
    private final FixedWindowRateLimiter loginLimiter;
    private final FixedWindowRateLimiter refreshLimiter;

    @Autowired
    public AuthenticationRateLimitInterceptor(
            SecurityErrorResponseWriter errorResponseWriter,
            @Value("${meridian.identity.rate-limit.login.max-requests:10}") int loginMaxRequests,
            @Value("${meridian.identity.rate-limit.login.window:1m}") Duration loginWindow,
            @Value("${meridian.identity.rate-limit.refresh.max-requests:30}") int refreshMaxRequests,
            @Value("${meridian.identity.rate-limit.refresh.window:1m}") Duration refreshWindow
    ) {
        this(
                errorResponseWriter,
                Clock.systemUTC(),
                loginMaxRequests,
                loginWindow,
                refreshMaxRequests,
                refreshWindow,
                MAX_TRACKED_CLIENTS_PER_ENDPOINT
        );
    }

    AuthenticationRateLimitInterceptor(
            SecurityErrorResponseWriter errorResponseWriter,
            Clock clock,
            int loginMaxRequests,
            Duration loginWindow,
            int refreshMaxRequests,
            Duration refreshWindow,
            int maxTrackedClientsPerEndpoint
    ) {
        this.errorResponseWriter = errorResponseWriter;
        this.loginLimiter = new FixedWindowRateLimiter(
                loginMaxRequests,
                loginWindow,
                maxTrackedClientsPerEndpoint,
                clock,
                "login"
        );
        this.refreshLimiter = new FixedWindowRateLimiter(
                refreshMaxRequests,
                refreshWindow,
                maxTrackedClientsPerEndpoint,
                clock,
                "refresh"
        );
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(LOGIN_PATH, REFRESH_PATH);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        FixedWindowRateLimiter limiter = limiterFor(request);
        if (limiter == null) {
            return true;
        }

        FixedWindowRateLimiter.RateLimitDecision decision = limiter.acquire(request.getRemoteAddr());
        if (decision.allowed()) {
            return true;
        }

        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ERROR_CODE,
                ERROR_MESSAGE
        );
        return false;
    }

    private FixedWindowRateLimiter limiterFor(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return switch (requestPath) {
            case LOGIN_PATH -> loginLimiter;
            case REFRESH_PATH -> refreshLimiter;
            default -> null;
        };
    }
}

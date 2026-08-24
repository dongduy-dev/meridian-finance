package com.meridian.platform.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String REQUEST_CORRELATION_MDC_KEY = "requestCorrelationId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestCorrelationId = effectiveRequestCorrelationId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestCorrelationId = MDC.get(REQUEST_CORRELATION_MDC_KEY);
        long startedAtNanos = System.nanoTime();

        MDC.put(REQUEST_CORRELATION_MDC_KEY, requestCorrelationId);
        response.setHeader(REQUEST_ID_HEADER, requestCorrelationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
                LOGGER.atInfo()
                        .addKeyValue("httpMethod", request.getMethod())
                        .addKeyValue("requestPath", request.getRequestURI())
                        .addKeyValue("httpStatus", response.getStatus())
                        .addKeyValue("durationMs", durationMs)
                        .log("HTTP request completed");
            } finally {
                restoreMdcValue(REQUEST_CORRELATION_MDC_KEY, previousRequestCorrelationId);
            }
        }
    }

    private static String effectiveRequestCorrelationId(String suppliedValue) {
        if (suppliedValue != null) {
            try {
                UUID parsed = UUID.fromString(suppliedValue);
                if (parsed.toString().equalsIgnoreCase(suppliedValue)) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed transport correlation value must not fail the business request.
            }
        }
        return UUID.randomUUID().toString();
    }

    private static void restoreMdcValue(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
}

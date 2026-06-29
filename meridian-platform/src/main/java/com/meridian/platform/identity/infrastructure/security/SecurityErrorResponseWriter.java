package com.meridian.platform.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class SecurityErrorResponseWriter {

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String errorCode,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"errorCode":"%s","message":"%s","path":"%s"}"""
                .formatted(
                        Instant.now(),
                        status,
                        escapeJson(errorCode),
                        escapeJson(message),
                        escapeJson(request.getRequestURI())
                ));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

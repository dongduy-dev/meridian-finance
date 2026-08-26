package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.PasswordResetMessage;
import com.meridian.platform.notification.application.port.in.SendPasswordResetUseCase;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class PasswordResetNotificationService implements SendPasswordResetUseCase {

    static final String SUBJECT = "Reset your Meridian password";

    private final EmailSenderPort emailSenderPort;
    private final String fromAddress;
    private final URI frontendBaseUri;

    public PasswordResetNotificationService(
            EmailSenderPort emailSenderPort,
            @Value("${meridian.notification.from-address:no-reply@meridian.local}") String fromAddress,
            @Value("${meridian.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.emailSenderPort = Objects.requireNonNull(emailSenderPort);
        this.fromAddress = requireNonBlank(fromAddress, "notification from-address");
        this.frontendBaseUri = requireHttpBaseUri(frontendBaseUrl);
    }

    @Override
    public void send(PasswordResetMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String recipient = requireNonBlank(message.recipientEmail(), "recipient email");
        String rawToken = requireNonBlank(message.rawToken(), "password-reset token");
        emailSenderPort.send(fromAddress, recipient, SUBJECT, body(rawToken));
    }

    String body(String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String base = frontendBaseUri.toString().replaceFirst("/+$", "");
        String resetLink = base + "/reset-password#token=" + encodedToken;
        return """
                Reset your Meridian password by opening this link:

                %s

                This link expires after a limited time. If you did not request a reset, ignore this message.
                """.formatted(resetLink);
    }

    private static URI requireHttpBaseUri(String value) {
        String normalized = requireNonBlank(value, "frontend base URL");
        URI uri = URI.create(normalized);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("frontend base URL must be an HTTP(S) origin or base path");
        }
        return uri;
    }

    private static String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        return value.trim();
    }
}

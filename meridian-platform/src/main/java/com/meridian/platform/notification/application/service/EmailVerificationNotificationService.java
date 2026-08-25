package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.EmailVerificationMessage;
import com.meridian.platform.notification.application.port.in.SendEmailVerificationUseCase;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class EmailVerificationNotificationService implements SendEmailVerificationUseCase {

    static final String SUBJECT = "Verify your Meridian email address";

    private final EmailSenderPort emailSenderPort;
    private final String fromAddress;
    private final URI frontendBaseUri;

    public EmailVerificationNotificationService(
            EmailSenderPort emailSenderPort,
            @Value("${meridian.notification.from-address:no-reply@meridian.local}") String fromAddress,
            @Value("${meridian.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.emailSenderPort = Objects.requireNonNull(emailSenderPort);
        this.fromAddress = requireNonBlank(fromAddress, "notification from-address");
        this.frontendBaseUri = requireHttpBaseUri(frontendBaseUrl);
    }

    @Override
    public void send(EmailVerificationMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String recipient = requireNonBlank(message.recipientEmail(), "recipient email");
        String rawToken = requireNonBlank(message.rawToken(), "verification token");
        emailSenderPort.send(fromAddress, recipient, SUBJECT, body(rawToken));
    }

    String body(String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String base = frontendBaseUri.toString().replaceFirst("/+$", "");
        String verificationLink = base + "/verify-email#token=" + encodedToken;
        return """
                Verify your Meridian email address by opening this link:

                %s

                This link expires after a limited time. If you did not register, ignore this message.
                """.formatted(verificationLink);
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

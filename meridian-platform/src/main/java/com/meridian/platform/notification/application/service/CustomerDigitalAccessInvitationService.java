package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.CustomerDigitalAccessInvitationMessage;
import com.meridian.platform.notification.application.port.in.SendCustomerDigitalAccessInvitationUseCase;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class CustomerDigitalAccessInvitationService implements SendCustomerDigitalAccessInvitationUseCase {
    private final EmailSenderPort sender;
    private final String fromAddress;
    private final URI frontendBaseUri;

    public CustomerDigitalAccessInvitationService(
            EmailSenderPort sender,
            @Value("${meridian.notification.from-address:no-reply@meridian.local}") String fromAddress,
            @Value("${meridian.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.sender = Objects.requireNonNull(sender);
        this.fromAddress = requireNonBlank(fromAddress);
        this.frontendBaseUri = URI.create(requireNonBlank(frontendBaseUrl));
        if (!("http".equalsIgnoreCase(frontendBaseUri.getScheme())
                || "https".equalsIgnoreCase(frontendBaseUri.getScheme()))
                || frontendBaseUri.getHost() == null || frontendBaseUri.getQuery() != null
                || frontendBaseUri.getFragment() != null) {
            throw new IllegalArgumentException("Customer frontend base URL must be an HTTP(S) origin or base path.");
        }
    }

    @Override
    public void send(CustomerDigitalAccessInvitationMessage message) {
        Objects.requireNonNull(message);
        String link = frontendBaseUri.toString().replaceFirst("/+$", "")
                + "/verify-email#token="
                + URLEncoder.encode(requireNonBlank(message.rawVerificationToken()), StandardCharsets.UTF_8);
        sender.send(fromAddress, requireNonBlank(message.recipientEmail()),
                "Enable your Meridian Customer Web access", """
                Meridian Staff enabled Customer Web access for your existing Meridian Customer record.
                Verify that you control this email address using this link:

                %s

                After verification, open Customer Web and use "Forgot password" to choose your password.
                The verification link expires after a limited time. If you did not expect this invitation, contact Meridian Staff.
                """.formatted(link));
    }

    private static String requireNonBlank(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invitation value must not be blank.");
        return value.trim();
    }
}

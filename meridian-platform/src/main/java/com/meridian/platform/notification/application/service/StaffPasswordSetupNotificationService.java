package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.SendStaffPasswordSetupUseCase;
import com.meridian.platform.notification.application.port.in.StaffPasswordSetupMessage;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class StaffPasswordSetupNotificationService implements SendStaffPasswordSetupUseCase {
    private final EmailSenderPort sender;
    private final String fromAddress;
    private final URI internalBaseUri;

    public StaffPasswordSetupNotificationService(
            EmailSenderPort sender,
            @Value("${meridian.notification.from-address:no-reply@meridian.local}") String fromAddress,
            @Value("${meridian.internal-frontend.base-url:http://localhost:5174}") String internalBaseUrl
    ) {
        this.sender = sender;
        this.fromAddress = requireNonBlank(fromAddress);
        this.internalBaseUri = URI.create(requireNonBlank(internalBaseUrl));
        if (!("http".equalsIgnoreCase(internalBaseUri.getScheme()) || "https".equalsIgnoreCase(internalBaseUri.getScheme()))
                || internalBaseUri.getHost() == null || internalBaseUri.getQuery() != null
                || internalBaseUri.getFragment() != null) {
            throw new IllegalArgumentException("Internal frontend base URL must be an HTTP(S) origin or base path.");
        }
    }

    @Override
    public void send(StaffPasswordSetupMessage message) {
        String recipient = requireNonBlank(message.recipientEmail());
        String token = requireNonBlank(message.rawToken());
        String link = internalBaseUri.toString().replaceFirst("/+$", "")
                + "/set-password#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        sender.send(fromAddress, recipient, "Set your Meridian Staff password", """
                Your Meridian Staff account is ready. Set your password using this link:

                %s

                This link expires after a limited time. If you did not expect an account, contact your administrator.
                """.formatted(link));
    }

    private static String requireNonBlank(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Email setup value must not be blank.");
        return value.trim();
    }
}

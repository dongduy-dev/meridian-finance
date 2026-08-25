package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.EmailVerificationMessage;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationNotificationServiceTest {

    @Test
    void rendersOnlyTheControlledFragmentLinkAndSendsThroughNotificationOwnedPort() {
        CapturingSender sender = new CapturingSender();
        EmailVerificationNotificationService service = new EmailVerificationNotificationService(
                sender,
                "no-reply@meridian.local",
                "http://localhost:5173/"
        );

        service.send(new EmailVerificationMessage("customer@example.com", "raw-token-value"));

        assertEquals("no-reply@meridian.local", sender.from);
        assertEquals("customer@example.com", sender.recipient);
        assertEquals(EmailVerificationNotificationService.SUBJECT, sender.subject);
        assertTrue(sender.body.contains("http://localhost:5173/verify-email#token=raw-token-value"));
        assertFalse(sender.body.contains("?token="));
    }

    private static final class CapturingSender implements EmailSenderPort {

        private String from;
        private String recipient;
        private String subject;
        private String body;

        @Override
        public void send(String fromAddress, String recipientAddress, String subject, String body) {
            this.from = fromAddress;
            this.recipient = recipientAddress;
            this.subject = subject;
            this.body = body;
        }
    }
}

package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.StaffPasswordSetupMessage;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffPasswordSetupNotificationServiceTest {
    @Test
    void sendsOnlyAnInternalWebFragmentLink() {
        CapturingSender sender = new CapturingSender();
        var service = new StaffPasswordSetupNotificationService(
                sender, "no-reply@meridian.local", "http://localhost:5174/");

        service.send(new StaffPasswordSetupMessage("staff@meridian.local", "opaque-token"));

        assertEquals("staff@meridian.local", sender.recipient);
        assertTrue(sender.body.contains("http://localhost:5174/set-password#token=opaque-token"));
        assertFalse(sender.body.contains("?token="));
        assertFalse(sender.body.contains("localhost:5173"));
        assertFalse(sender.body.contains("password_hash"));
    }

    private static final class CapturingSender implements EmailSenderPort {
        private String recipient;
        private String body;

        @Override
        public void send(String fromAddress, String recipientAddress, String subject, String body) {
            this.recipient = recipientAddress;
            this.body = body;
        }
    }
}

package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.CustomerDigitalAccessInvitationMessage;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDigitalAccessInvitationServiceTest {
    @Test
    void sendsCustomerVerificationFragmentAndPasswordRecoveryInstructions() {
        CapturingSender sender = new CapturingSender();
        var service = new CustomerDigitalAccessInvitationService(
                sender, "no-reply@meridian.local", "http://localhost:5173/");

        service.send(new CustomerDigitalAccessInvitationMessage("customer@meridian.local", "opaque-token"));

        assertEquals("customer@meridian.local", sender.recipient);
        assertTrue(sender.body.contains("http://localhost:5173/verify-email#token=opaque-token"));
        assertTrue(sender.body.contains("existing Meridian Customer record"));
        assertTrue(sender.body.contains("Forgot password"));
        assertFalse(sender.body.contains("?token="));
        assertFalse(sender.body.contains("identity reference"));
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

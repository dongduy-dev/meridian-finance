package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.CustomerDigitalAccessInvitationMessage;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerDigitalAccessInvitationServiceTest {
    @Test
    void sendsOneActivationFragmentWithIndependentlyEncodedSecrets() {
        CapturingSender sender = new CapturingSender();
        var service = new CustomerDigitalAccessInvitationService(
                sender, "no-reply@meridian.local", "http://localhost:5173/");
        service.send(new CustomerDigitalAccessInvitationMessage("customer@meridian.local", "verify+&=/", "setup+&=/"));
        assertEquals(1, sender.calls);
        assertEquals("customer@meridian.local", sender.recipient);
        assertEquals("Enable your Meridian Customer Web access", sender.subject);
        assertTrue(sender.body.contains("http://localhost:5173/activate-access#verificationToken=verify%2B%26%3D%2F&setupToken=setup%2B%26%3D%2F"));
        assertEquals(1, sender.body.split("http://localhost:5173", -1).length - 1);
        assertTrue(sender.body.contains("existing Meridian Customer record"));
        assertTrue(sender.body.contains("verify your email and set your password"));
        for (String forbidden : new String[] {"Forgot password", "?", "identity reference", "customerId", "userId", "digest", "placeholder", "password-reset"}) {
            assertFalse(sender.body.contains(forbidden), forbidden);
        }
    }

    private static final class CapturingSender implements EmailSenderPort {
        private int calls;
        private String recipient;
        private String subject;
        private String body;

        @Override
        public void send(String fromAddress, String recipientAddress, String subject, String body) {
            calls++;
            this.recipient = recipientAddress;
            this.subject = subject;
            this.body = body;
        }
    }
}

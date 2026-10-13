package com.meridian.platform.notification.application.service;

import com.meridian.platform.notification.application.port.in.*;
import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ProductionEmailDestinationsTest {
    @Test
    void allFourExistingMessagesKeepTheCorrectHttpsHostAndEncodedFragment() {
        var bodies = new ArrayList<String>();
        EmailSenderPort sender = (from, recipient, subject, body) -> {
            assertEquals("notifications@meridianfinance.dev", from);
            bodies.add(body);
        };
        String from = "notifications@meridianfinance.dev";
        String customer = "https://meridianfinance.dev", internal = "https://internal.meridianfinance.dev";
        new EmailVerificationNotificationService(sender, from, customer)
                .send(new EmailVerificationMessage("synthetic@example.invalid", "encoded+&"));
        new PasswordResetNotificationService(sender, from, customer)
                .send(new PasswordResetMessage("synthetic@example.invalid", "encoded+&"));
        new StaffPasswordSetupNotificationService(sender, from, internal)
                .send(new StaffPasswordSetupMessage("synthetic@example.invalid", "encoded+&"));
        new CustomerDigitalAccessInvitationService(sender, from, customer)
                .send(new CustomerDigitalAccessInvitationMessage("synthetic@example.invalid", "encoded+&", "encoded+&"));
        String[] links = {customer + "/verify-email#token=encoded%2B%26", customer + "/reset-password#token=encoded%2B%26",
                internal + "/set-password#token=encoded%2B%26",
                customer + "/activate-access#verificationToken=encoded%2B%26&setupToken=encoded%2B%26"};
        for (int i = 0; i < links.length; i++) {
            assertTrue(bodies.get(i).contains(links[i]));
            assertFalse(bodies.get(i).contains("?token="));
        }
    }
}

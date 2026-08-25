package com.meridian.platform.identity.application.port.out;

public interface EmailVerificationNotificationPort {

    void sendVerificationEmail(String recipientEmail, String rawToken);
}

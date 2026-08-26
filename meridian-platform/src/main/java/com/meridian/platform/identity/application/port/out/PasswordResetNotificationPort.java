package com.meridian.platform.identity.application.port.out;

public interface PasswordResetNotificationPort {

    void sendPasswordResetEmail(String recipientEmail, String rawToken);
}

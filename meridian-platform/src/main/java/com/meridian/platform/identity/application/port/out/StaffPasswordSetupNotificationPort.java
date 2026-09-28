package com.meridian.platform.identity.application.port.out;

public interface StaffPasswordSetupNotificationPort {
    void sendSetupEmail(String recipientEmail, String rawToken);
}

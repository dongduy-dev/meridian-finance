package com.meridian.platform.notification.application.port.in;

public record StaffPasswordSetupMessage(String recipientEmail, String rawToken) {
}

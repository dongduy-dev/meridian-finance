package com.meridian.platform.notification.application.port.in;

public record PasswordResetMessage(String recipientEmail, String rawToken) {
}

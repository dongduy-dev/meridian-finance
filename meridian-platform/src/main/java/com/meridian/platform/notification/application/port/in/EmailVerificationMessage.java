package com.meridian.platform.notification.application.port.in;

public record EmailVerificationMessage(String recipientEmail, String rawToken) {
}

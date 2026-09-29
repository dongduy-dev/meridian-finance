package com.meridian.platform.notification.application.port.in;

public record CustomerDigitalAccessInvitationMessage(String recipientEmail, String rawVerificationToken) {
}

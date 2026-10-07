package com.meridian.platform.identity.application.port.out;

public interface CustomerDigitalAccessInvitationPort {
    void sendInvitation(String recipientEmail, String rawVerificationToken, String rawSetupToken);
}

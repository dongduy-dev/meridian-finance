package com.meridian.platform.identity.infrastructure.adapter.out.notification;

import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessInvitationPort;
import com.meridian.platform.notification.application.port.in.CustomerDigitalAccessInvitationMessage;
import com.meridian.platform.notification.application.port.in.SendCustomerDigitalAccessInvitationUseCase;
import org.springframework.stereotype.Component;

@Component
public class CustomerDigitalAccessInvitationAdapter implements CustomerDigitalAccessInvitationPort {
    private final SendCustomerDigitalAccessInvitationUseCase notification;

    public CustomerDigitalAccessInvitationAdapter(SendCustomerDigitalAccessInvitationUseCase notification) {
        this.notification = notification;
    }

    @Override
    public void sendInvitation(String recipientEmail, String rawVerificationToken) {
        notification.send(new CustomerDigitalAccessInvitationMessage(recipientEmail, rawVerificationToken));
    }
}

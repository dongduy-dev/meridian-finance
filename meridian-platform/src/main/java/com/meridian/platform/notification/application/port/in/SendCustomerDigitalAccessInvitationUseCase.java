package com.meridian.platform.notification.application.port.in;

public interface SendCustomerDigitalAccessInvitationUseCase {
    void send(CustomerDigitalAccessInvitationMessage message);
}

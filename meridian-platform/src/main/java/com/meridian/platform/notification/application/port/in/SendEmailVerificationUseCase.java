package com.meridian.platform.notification.application.port.in;

public interface SendEmailVerificationUseCase {

    void send(EmailVerificationMessage message);
}

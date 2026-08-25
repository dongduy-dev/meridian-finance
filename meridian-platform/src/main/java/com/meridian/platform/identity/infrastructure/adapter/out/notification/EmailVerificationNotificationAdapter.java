package com.meridian.platform.identity.infrastructure.adapter.out.notification;

import com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort;
import com.meridian.platform.notification.application.port.in.EmailVerificationMessage;
import com.meridian.platform.notification.application.port.in.SendEmailVerificationUseCase;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationNotificationAdapter implements EmailVerificationNotificationPort {

    private final SendEmailVerificationUseCase sendEmailVerificationUseCase;

    public EmailVerificationNotificationAdapter(SendEmailVerificationUseCase sendEmailVerificationUseCase) {
        this.sendEmailVerificationUseCase = sendEmailVerificationUseCase;
    }

    @Override
    public void sendVerificationEmail(String recipientEmail, String rawToken) {
        sendEmailVerificationUseCase.send(new EmailVerificationMessage(recipientEmail, rawToken));
    }
}

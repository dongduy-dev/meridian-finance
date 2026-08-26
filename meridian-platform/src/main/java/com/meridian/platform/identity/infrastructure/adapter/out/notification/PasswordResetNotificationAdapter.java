package com.meridian.platform.identity.infrastructure.adapter.out.notification;

import com.meridian.platform.identity.application.port.out.PasswordResetNotificationPort;
import com.meridian.platform.notification.application.port.in.PasswordResetMessage;
import com.meridian.platform.notification.application.port.in.SendPasswordResetUseCase;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetNotificationAdapter implements PasswordResetNotificationPort {

    private final SendPasswordResetUseCase sendPasswordResetUseCase;

    public PasswordResetNotificationAdapter(SendPasswordResetUseCase sendPasswordResetUseCase) {
        this.sendPasswordResetUseCase = sendPasswordResetUseCase;
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String rawToken) {
        sendPasswordResetUseCase.send(new PasswordResetMessage(recipientEmail, rawToken));
    }
}

package com.meridian.platform.notification.application.port.in;

public interface SendPasswordResetUseCase {

    void send(PasswordResetMessage message);
}

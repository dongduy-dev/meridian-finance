package com.meridian.platform.identity.application.port.out;

public interface PasswordResetTokenCodecPort {

    GeneratedPasswordResetToken generate();

    String digest(String rawToken);
}

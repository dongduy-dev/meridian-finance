package com.meridian.platform.identity.application.port.out;

public interface EmailVerificationTokenCodecPort {

    GeneratedEmailVerificationToken generate();

    String digest(String rawToken);
}

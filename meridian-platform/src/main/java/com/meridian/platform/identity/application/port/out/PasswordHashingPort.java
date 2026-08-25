package com.meridian.platform.identity.application.port.out;

public interface PasswordHashingPort {

    String hash(String rawPassword);
}

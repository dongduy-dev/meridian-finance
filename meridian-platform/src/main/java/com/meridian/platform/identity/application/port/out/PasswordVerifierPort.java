package com.meridian.platform.identity.application.port.out;

public interface PasswordVerifierPort {

    boolean matches(String rawPassword, String passwordHash);
}

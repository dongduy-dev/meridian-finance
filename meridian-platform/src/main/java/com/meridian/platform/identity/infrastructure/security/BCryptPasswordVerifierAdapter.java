package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.PasswordVerifierPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordVerifierAdapter implements PasswordVerifierPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordVerifierAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}

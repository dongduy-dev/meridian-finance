package com.meridian.platform.identity.application.dto;

import java.util.UUID;

public record CustomerDigitalAccessDto(UUID customerId, boolean enabled, String email, boolean emailVerified) {
    public static CustomerDigitalAccessDto absent(UUID customerId) {
        return new CustomerDigitalAccessDto(customerId, false, null, false);
    }
}

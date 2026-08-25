package com.meridian.platform.identity.application.dto;

public record CustomerRegistrationResponse(boolean emailVerificationRequired) {

    public static CustomerRegistrationResponse verificationRequired() {
        return new CustomerRegistrationResponse(true);
    }
}

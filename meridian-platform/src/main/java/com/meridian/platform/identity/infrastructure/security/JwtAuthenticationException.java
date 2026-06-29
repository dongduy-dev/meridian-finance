package com.meridian.platform.identity.infrastructure.security;

public class JwtAuthenticationException extends RuntimeException {

    private final String errorCode;

    public JwtAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

package com.meridian.platform.shared.domain.exception;

public class AuthorizationException extends RuntimeException {

    private final String errorCode;

    public AuthorizationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

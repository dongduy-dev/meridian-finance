package com.meridian.platform.shared.domain.exception;

public class BusinessStateConflictException extends RuntimeException {

    private final String errorCode;

    public BusinessStateConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

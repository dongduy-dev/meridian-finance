package com.meridian.platform.shared.domain.exception;

public class EntityNotFoundException extends RuntimeException {

    private final String errorCode;

    public EntityNotFoundException(String message) {
        super(message);
        this.errorCode = "RESOURCE_NOT_FOUND";
    }

    public EntityNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

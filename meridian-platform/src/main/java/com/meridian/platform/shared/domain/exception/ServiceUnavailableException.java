package com.meridian.platform.shared.domain.exception;

public class ServiceUnavailableException extends RuntimeException {

    private final String errorCode;

    public ServiceUnavailableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

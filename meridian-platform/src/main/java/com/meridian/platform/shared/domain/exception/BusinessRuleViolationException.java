package com.meridian.platform.shared.domain.exception;

public class BusinessRuleViolationException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

package com.meridian.platform.document.domain.model;

public enum OcrFailureCategory {
    SOURCE_NOT_FOUND,
    SOURCE_INTEGRITY_MISMATCH,
    UNSUPPORTED_SOURCE,
    PROVIDER_UNAVAILABLE,
    PROVIDER_REJECTED,
    PROVIDER_ERROR,
    CONFIGURATION_ERROR
}

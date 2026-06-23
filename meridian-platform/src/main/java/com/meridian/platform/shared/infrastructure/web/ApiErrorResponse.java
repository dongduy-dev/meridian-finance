package com.meridian.platform.shared.infrastructure.web;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path
) {
}

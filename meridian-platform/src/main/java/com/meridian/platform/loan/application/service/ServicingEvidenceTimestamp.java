package com.meridian.platform.loan.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

final class ServicingEvidenceTimestamp {

    private ServicingEvidenceTimestamp() {
    }

    static boolean same(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return normalizeForPersistence(left).equals(normalizeForPersistence(right));
    }

    static LocalDateTime normalizeForPersistence(LocalDateTime timestamp) {
        return timestamp.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }
}

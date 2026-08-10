package com.meridian.platform.loan.application.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicingEvidenceTimestampTest {

    @Test
    void normalizesNewEvidenceToPostgreSqlMicrosecondPrecision() {
        assertEquals(
                LocalDateTime.parse("2026-08-10T05:06:10.568457"),
                ServicingEvidenceTimestamp.normalizeForPersistence(
                        LocalDateTime.parse("2026-08-10T05:06:10.5684568")
                )
        );
    }

    @Test
    void matchesTimestampValuesAtPostgreSqlMicrosecondPrecision() {
        assertTrue(ServicingEvidenceTimestamp.same(
                LocalDateTime.parse("2026-08-10T05:06:10.5684561"),
                LocalDateTime.parse("2026-08-10T05:06:10.568456")
        ));
        assertTrue(ServicingEvidenceTimestamp.same(
                LocalDateTime.parse("2026-08-10T05:06:10.5684568"),
                LocalDateTime.parse("2026-08-10T05:06:10.568457")
        ));
    }

    @Test
    void rejectsDifferentPersistedTimestampValues() {
        assertFalse(ServicingEvidenceTimestamp.same(
                LocalDateTime.parse("2026-08-10T05:06:10.5684564"),
                LocalDateTime.parse("2026-08-10T05:06:10.568457")
        ));
        assertFalse(ServicingEvidenceTimestamp.same(null, LocalDateTime.MIN));
    }
}

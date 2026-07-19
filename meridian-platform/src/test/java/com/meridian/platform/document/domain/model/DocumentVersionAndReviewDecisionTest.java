package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentVersionAndReviewDecisionTest {

    @Test
    void acceptsImmutableVersionMetadataForAllowedFile() {
        DocumentVersion version = version("evidence.pdf", "application/pdf", 1024);

        assertEquals(1, version.versionNumber());
        assertEquals("application/pdf", version.detectedMimeType());
    }

    @Test
    void rejectsUnsafeFilenameMimeAndSize() {
        assertEquals("INVALID_DOCUMENT_UPLOAD", assertThrows(
                BusinessRuleViolationException.class,
                () -> version("../evidence.pdf", "application/pdf", 1024)
        ).getErrorCode());
        assertEquals("INVALID_DOCUMENT_UPLOAD", assertThrows(
                BusinessRuleViolationException.class,
                () -> version("evidence.svg", "image/svg+xml", 1024)
        ).getErrorCode());
        assertEquals("INVALID_DOCUMENT_UPLOAD", assertThrows(
                BusinessRuleViolationException.class,
                () -> version("evidence.pdf", "application/pdf", DocumentVersion.MAX_BYTE_SIZE + 1)
        ).getErrorCode());
    }

    @Test
    void waiverRequiresControlledReasonAndOtherOutcomesRejectIt() {
        UUID itemId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 8, 0);

        assertEquals("DOCUMENT_WAIVER_REASON_REQUIRED", assertThrows(
                BusinessRuleViolationException.class,
                () -> new DocumentReviewDecision(
                        UUID.randomUUID(), itemId, versionId, UUID.randomUUID(),
                        DocumentReviewOutcome.WAIVE_DOCUMENT, null, null, reviewerId, now
                )
        ).getErrorCode());
        assertEquals("DOCUMENT_WAIVER_REASON_NOT_ALLOWED", assertThrows(
                BusinessRuleViolationException.class,
                () -> new DocumentReviewDecision(
                        UUID.randomUUID(), itemId, versionId, UUID.randomUUID(),
                        DocumentReviewOutcome.ACCEPT_DOCUMENT,
                        DocumentWaiverReasonCode.DOCUMENT_NOT_APPLICABLE,
                        null, reviewerId, now
                )
        ).getErrorCode());
    }

    private DocumentVersion version(String filename, String mimeType, long size) {
        return new DocumentVersion(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                null,
                filename,
                mimeType,
                mimeType,
                size,
                "a".repeat(64),
                "opaque-key",
                DocumentUploaderActorType.CUSTOMER,
                UUID.randomUUID(),
                LocalDateTime.of(2026, 7, 19, 8, 0)
        );
    }
}

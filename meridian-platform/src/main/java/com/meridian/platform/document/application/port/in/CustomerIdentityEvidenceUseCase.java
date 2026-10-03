package com.meridian.platform.document.application.port.in;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/** Internal purpose-limited contract. Browser authorization belongs to the Customer verification workflow. */
public interface CustomerIdentityEvidenceUseCase {
    record Evidence(UUID versionId, int versionNumber, String filename, String mimeType, long byteSize, LocalDateTime uploadedAt) {}
    record Content(String filename, String mimeType, long byteSize, InputStream content) {}
    Evidence store(UUID customerId, UUID requestId, UUID baseline, InputStream content, String mime, String filename, UUID actor, LocalDateTime now);
    UUID lockIntakeCustomer(UUID caseId, boolean requireOpen);
    Evidence requireCurrent(UUID customerId, UUID caseId, UUID versionId);
    Evidence metadata(UUID customerId, UUID caseId, UUID versionId);
    Content read(UUID customerId, UUID caseId, UUID versionId);
}

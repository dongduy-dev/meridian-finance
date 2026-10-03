package com.meridian.platform.customer.application.port.out;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

public interface CustomerIdentityEvidencePort {
    record Evidence(UUID versionId, int versionNumber, String filename, String mimeType, long byteSize, LocalDateTime uploadedAt) {}
    record Content(String filename, String mimeType, long byteSize, InputStream content) {}
    Evidence store(UUID customerId, UUID requestId, UUID baselineVersionId, InputStream content, String mimeType, String filename, UUID actor, LocalDateTime now);
    UUID lockIntakeCustomer(UUID caseId, boolean requireOpen);
    Evidence requireCurrent(UUID customerId, UUID caseId, UUID versionId);
    Evidence metadata(UUID customerId, UUID caseId, UUID versionId);
    Content read(UUID customerId, UUID caseId, UUID versionId);
}

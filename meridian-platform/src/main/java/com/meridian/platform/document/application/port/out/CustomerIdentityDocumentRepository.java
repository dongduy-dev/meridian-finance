package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.CustomerIdentityDocumentVersion;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityDocumentRepository {
    record Document(UUID id, UUID customerId, UUID currentVersionId) {}
    void lockUploadRequest(UUID requestId);
    Document lockOrCreate(UUID customerId, LocalDateTime now);
    Optional<Document> find(UUID customerId, boolean lock);
    Optional<CustomerIdentityDocumentVersion> findVersion(UUID id);
    Optional<CustomerIdentityDocumentVersion> findUpload(UUID requestId);
    void saveVersion(CustomerIdentityDocumentVersion version);
    void advance(UUID documentId, UUID versionId, LocalDateTime now);
}

package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentVersionRepository extends JpaRepository<DocumentVersionJpaEntity, UUID> {
    Optional<DocumentVersionJpaEntity> findByUploadRequestId(UUID uploadRequestId);

    boolean existsByStorageKey(String storageKey);
}

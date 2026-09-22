package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaAssistedActionDocumentVersionRepository
        extends JpaRepository<AssistedActionDocumentVersionJpaEntity, UUID> {

    Optional<AssistedActionDocumentVersionJpaEntity> findByUploadRequestId(UUID uploadRequestId);
}

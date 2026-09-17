package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIntakeDocumentVersionRepository
        extends JpaRepository<IntakeDocumentVersionJpaEntity, UUID> {

    Optional<IntakeDocumentVersionJpaEntity> findByUploadRequestId(UUID uploadRequestId);

    List<IntakeDocumentVersionJpaEntity> findAllByIntakeDocumentIdOrderByVersionNumberAscIdAsc(UUID documentId);
}

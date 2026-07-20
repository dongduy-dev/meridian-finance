package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    Optional<DocumentJpaEntity> findByChecklistItemId(UUID checklistItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from DocumentJpaEntity document where document.checklistItemId = :checklistItemId")
    Optional<DocumentJpaEntity> findByChecklistItemIdForUpdate(@Param("checklistItemId") UUID checklistItemId);
}

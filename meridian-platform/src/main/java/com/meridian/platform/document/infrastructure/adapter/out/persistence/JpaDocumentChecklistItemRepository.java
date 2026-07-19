package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentChecklistItemRepository extends JpaRepository<DocumentChecklistItemJpaEntity, UUID> {

    List<DocumentChecklistItemJpaEntity> findAllByChecklistIdOrderByCreatedAt(UUID checklistId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from DocumentChecklistItemJpaEntity item where item.id = :id")
    Optional<DocumentChecklistItemJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}

package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentReviewDecisionRepository
        extends JpaRepository<DocumentReviewDecisionJpaEntity, UUID> {
    Optional<DocumentReviewDecisionJpaEntity> findByReviewRequestId(UUID reviewRequestId);

    List<DocumentReviewDecisionJpaEntity> findAllByChecklistItemIdOrderByDecidedAtAscIdAsc(UUID checklistItemId);
}

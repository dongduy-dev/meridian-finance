package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentReviewDecisionRepository
        extends JpaRepository<DocumentReviewDecisionJpaEntity, UUID> {
    Optional<DocumentReviewDecisionJpaEntity> findByReviewRequestId(UUID reviewRequestId);
}

package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaOcrReviewRepository extends JpaRepository<OcrReviewJpaEntity, UUID> {

    Optional<OcrReviewJpaEntity> findByOcrResultId(UUID resultId);
}

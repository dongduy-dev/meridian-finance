package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaOcrJobRepository extends JpaRepository<OcrJobJpaEntity, UUID> {

    Optional<OcrJobJpaEntity> findByIntakeDocumentVersionId(UUID versionId);

    @Query(value = "SELECT disposition FROM ocr_results WHERE ocr_job_id = :jobId", nativeQuery = true)
    Optional<String> findResultDisposition(@Param("jobId") UUID jobId);
}

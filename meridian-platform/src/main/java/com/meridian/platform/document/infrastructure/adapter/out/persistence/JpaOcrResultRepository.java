package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaOcrResultRepository extends JpaRepository<OcrResultJpaEntity, UUID> {

    Optional<OcrResultJpaEntity> findByOcrJobId(UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from OcrResultJpaEntity result where result.id = :resultId")
    Optional<OcrResultJpaEntity> findByIdForUpdate(@Param("resultId") UUID resultId);
}

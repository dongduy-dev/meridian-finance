package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIntakeDocumentRepository extends JpaRepository<IntakeDocumentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from IntakeDocumentJpaEntity document "
            + "where document.assistedOriginationCaseId = :caseId and document.evidenceType = :evidenceType")
    Optional<IntakeDocumentJpaEntity> findByCaseAndTypeForUpdate(
            @Param("caseId") UUID caseId,
            @Param("evidenceType") IntakeEvidenceType evidenceType
    );

    List<IntakeDocumentJpaEntity> findAllByAssistedOriginationCaseIdOrderByEvidenceTypeAsc(UUID caseId);
}

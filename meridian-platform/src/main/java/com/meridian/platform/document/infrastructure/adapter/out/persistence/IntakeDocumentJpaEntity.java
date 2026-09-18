package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "intake_documents")
public class IntakeDocumentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "assisted_origination_case_id", nullable = false)
    private UUID assistedOriginationCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private IntakeEvidenceType evidenceType;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected IntakeDocumentJpaEntity() {
    }

    IntakeDocumentJpaEntity(IntakeDocument value) {
        update(value);
    }

    void update(IntakeDocument value) {
        id = value.id();
        assistedOriginationCaseId = value.assistedOriginationCaseId();
        evidenceType = value.evidenceType();
        currentVersionId = value.currentVersionId();
        createdAt = value.createdAt();
        updatedAt = value.updatedAt();
    }

    IntakeDocument toDomain() {
        return new IntakeDocument(id, assistedOriginationCaseId, evidenceType,
                currentVersionId, createdAt, updatedAt);
    }
}

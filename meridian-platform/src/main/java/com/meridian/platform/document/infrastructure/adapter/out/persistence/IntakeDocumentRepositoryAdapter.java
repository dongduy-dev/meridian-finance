package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IntakeDocumentRepositoryAdapter implements IntakeDocumentRepository {

    private final JpaIntakeDocumentRepository documents;
    private final JpaIntakeDocumentVersionRepository versions;

    public IntakeDocumentRepositoryAdapter(
            JpaIntakeDocumentRepository documents,
            JpaIntakeDocumentVersionRepository versions
    ) {
        this.documents = documents;
        this.versions = versions;
    }

    @Override
    public IntakeDocument saveDocument(IntakeDocument document) {
        IntakeDocumentJpaEntity entity = documents.findById(document.id())
                .orElseGet(() -> new IntakeDocumentJpaEntity(document));
        entity.update(document);
        return documents.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<IntakeDocument> findByCaseAndTypeForUpdate(UUID caseId, IntakeEvidenceType evidenceType) {
        return documents.findByCaseAndTypeForUpdate(caseId, evidenceType).map(IntakeDocumentJpaEntity::toDomain);
    }

    @Override
    public Optional<IntakeDocument> findByCaseAndType(UUID caseId, IntakeEvidenceType evidenceType) {
        return documents.findByAssistedOriginationCaseIdAndEvidenceType(caseId, evidenceType)
                .map(IntakeDocumentJpaEntity::toDomain);
    }

    @Override
    public List<IntakeDocument> findByCase(UUID caseId) {
        return documents.findAllByAssistedOriginationCaseIdOrderByEvidenceTypeAsc(caseId)
                .stream().map(IntakeDocumentJpaEntity::toDomain).toList();
    }

    @Override
    public IntakeDocumentVersion saveVersion(IntakeDocumentVersion version) {
        return versions.save(new IntakeDocumentVersionJpaEntity(version)).toDomain();
    }

    @Override
    public Optional<IntakeDocumentVersion> findVersionById(UUID versionId) {
        return versions.findById(versionId).map(IntakeDocumentVersionJpaEntity::toDomain);
    }

    @Override
    public Optional<IntakeDocumentVersion> findVersionByUploadRequestId(UUID uploadRequestId) {
        return versions.findByUploadRequestId(uploadRequestId).map(IntakeDocumentVersionJpaEntity::toDomain);
    }

    @Override
    public List<IntakeDocumentVersion> findVersionsByDocumentId(UUID documentId) {
        return versions.findAllByIntakeDocumentIdOrderByVersionNumberAscIdAsc(documentId)
                .stream().map(IntakeDocumentVersionJpaEntity::toDomain).toList();
    }
}

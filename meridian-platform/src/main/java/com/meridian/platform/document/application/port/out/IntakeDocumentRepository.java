package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntakeDocumentRepository {

    IntakeDocument saveDocument(IntakeDocument document);

    Optional<IntakeDocument> findByCaseAndTypeForUpdate(UUID caseId, IntakeEvidenceType evidenceType);

    List<IntakeDocument> findByCase(UUID caseId);

    IntakeDocumentVersion saveVersion(IntakeDocumentVersion version);

    Optional<IntakeDocumentVersion> findVersionById(UUID versionId);

    Optional<IntakeDocumentVersion> findVersionByUploadRequestId(UUID uploadRequestId);

    List<IntakeDocumentVersion> findVersionsByDocumentId(UUID documentId);
}

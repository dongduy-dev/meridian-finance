package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    StoredDocument saveDocument(StoredDocument document);

    Optional<StoredDocument> findDocumentByChecklistItemId(UUID checklistItemId);

    Optional<StoredDocument> findDocumentByChecklistItemIdForUpdate(UUID checklistItemId);

    DocumentVersion saveVersion(DocumentVersion version);

    Optional<DocumentVersion> findVersionById(UUID versionId);

    Optional<DocumentVersion> findVersionByUploadRequestId(UUID uploadRequestId);

    List<DocumentVersion> findVersionsByDocumentId(UUID documentId);

    DocumentReviewDecision saveReviewDecision(DocumentReviewDecision decision);

    Optional<DocumentReviewDecision> findReviewDecisionById(UUID reviewDecisionId);

    Optional<DocumentReviewDecision> findReviewDecisionByReviewRequestId(UUID reviewRequestId);

    List<DocumentReviewDecision> findReviewDecisionsByChecklistItemId(UUID checklistItemId);

    boolean existsStorageReference(String storageKey);
}

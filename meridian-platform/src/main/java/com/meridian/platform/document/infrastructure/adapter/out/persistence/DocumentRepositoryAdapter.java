package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepositoryAdapter implements DocumentRepository {

    private final JpaDocumentRepository documentRepository;
    private final JpaDocumentVersionRepository versionRepository;
    private final JpaDocumentReviewDecisionRepository reviewDecisionRepository;

    public DocumentRepositoryAdapter(
            JpaDocumentRepository documentRepository,
            JpaDocumentVersionRepository versionRepository,
            JpaDocumentReviewDecisionRepository reviewDecisionRepository
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.reviewDecisionRepository = reviewDecisionRepository;
    }

    @Override
    public StoredDocument saveDocument(StoredDocument document) {
        DocumentJpaEntity entity = documentRepository.findById(document.id())
                .orElseGet(() -> new DocumentJpaEntity(document));
        entity.updateFrom(document);
        return documentRepository.save(entity).toDomain();
    }

    @Override
    public Optional<StoredDocument> findDocumentByChecklistItemId(UUID checklistItemId) {
        return documentRepository.findByChecklistItemId(checklistItemId)
                .map(DocumentJpaEntity::toDomain);
    }

    @Override
    public Optional<StoredDocument> findDocumentByChecklistItemIdForUpdate(UUID checklistItemId) {
        return documentRepository.findByChecklistItemIdForUpdate(checklistItemId)
                .map(DocumentJpaEntity::toDomain);
    }

    @Override
    public DocumentVersion saveVersion(DocumentVersion version) {
        return versionRepository.save(new DocumentVersionJpaEntity(version)).toDomain();
    }

    @Override
    public Optional<DocumentVersion> findVersionById(UUID versionId) {
        return versionRepository.findById(versionId).map(DocumentVersionJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentVersion> findVersionByUploadRequestId(UUID uploadRequestId) {
        return versionRepository.findByUploadRequestId(uploadRequestId)
                .map(DocumentVersionJpaEntity::toDomain);
    }

    @Override
    public List<DocumentVersion> findVersionsByDocumentId(UUID documentId) {
        return versionRepository.findAllByDocumentIdOrderByVersionNumberAscIdAsc(documentId).stream()
                .map(DocumentVersionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public DocumentReviewDecision saveReviewDecision(DocumentReviewDecision decision) {
        return reviewDecisionRepository.save(new DocumentReviewDecisionJpaEntity(decision)).toDomain();
    }

    @Override
    public Optional<DocumentReviewDecision> findReviewDecisionById(UUID reviewDecisionId) {
        return reviewDecisionRepository.findById(reviewDecisionId)
                .map(DocumentReviewDecisionJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentReviewDecision> findReviewDecisionByReviewRequestId(UUID reviewRequestId) {
        return reviewDecisionRepository.findByReviewRequestId(reviewRequestId)
                .map(DocumentReviewDecisionJpaEntity::toDomain);
    }

    @Override
    public List<DocumentReviewDecision> findReviewDecisionsByChecklistItemId(UUID checklistItemId) {
        return reviewDecisionRepository
                .findAllByChecklistItemIdOrderByDecidedAtAscIdAsc(checklistItemId).stream()
                .map(DocumentReviewDecisionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsStorageReference(String storageKey) {
        return versionRepository.existsByStorageKey(storageKey);
    }
}

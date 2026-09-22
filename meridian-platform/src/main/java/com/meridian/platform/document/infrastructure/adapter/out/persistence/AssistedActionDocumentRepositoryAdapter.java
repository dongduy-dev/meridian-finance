package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.AssistedActionDocumentRepository;
import com.meridian.platform.document.domain.model.AssistedActionDocument;
import com.meridian.platform.document.domain.model.AssistedActionDocumentVersion;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AssistedActionDocumentRepositoryAdapter implements AssistedActionDocumentRepository {
    private final JpaAssistedActionDocumentRepository documents;
    private final JpaAssistedActionDocumentVersionRepository versions;

    public AssistedActionDocumentRepositoryAdapter(
            JpaAssistedActionDocumentRepository documents,
            JpaAssistedActionDocumentVersionRepository versions
    ) {
        this.documents = documents;
        this.versions = versions;
    }

    @Override
    public AssistedActionDocument saveDocument(AssistedActionDocument document) {
        AssistedActionDocumentJpaEntity entity = documents.findById(document.id())
                .orElseGet(() -> new AssistedActionDocumentJpaEntity(document));
        entity.update(document);
        return documents.saveAndFlush(entity).toDomain();
    }

    @Override public Optional<AssistedActionDocument> findOfferDocumentForUpdate(UUID applicationId, UUID offerId) {
        return documents.findOfferForUpdate(applicationId, offerId).map(AssistedActionDocumentJpaEntity::toDomain);
    }
    @Override public Optional<AssistedActionDocument> findOfferDocument(UUID applicationId, UUID offerId) {
        return documents.findByLoanApplicationIdAndApprovedOfferId(applicationId, offerId)
                .map(AssistedActionDocumentJpaEntity::toDomain);
    }
    @Override public Optional<AssistedActionDocument> findContractDocumentForUpdate(
            UUID applicationId, UUID contractId, int version) {
        return documents.findContractForUpdate(applicationId, contractId, version)
                .map(AssistedActionDocumentJpaEntity::toDomain);
    }
    @Override public Optional<AssistedActionDocument> findContractDocument(
            UUID applicationId, UUID contractId, int version) {
        return documents.findByLoanApplicationIdAndLoanContractIdAndContractVersion(applicationId, contractId, version)
                .map(AssistedActionDocumentJpaEntity::toDomain);
    }
    @Override public AssistedActionDocumentVersion saveVersion(AssistedActionDocumentVersion version) {
        return versions.save(new AssistedActionDocumentVersionJpaEntity(version)).toDomain();
    }
    @Override public Optional<AssistedActionDocumentVersion> findVersionById(UUID versionId) {
        return versions.findById(versionId).map(AssistedActionDocumentVersionJpaEntity::toDomain);
    }
    @Override public Optional<AssistedActionDocumentVersion> findVersionByUploadRequestId(UUID requestId) {
        return versions.findByUploadRequestId(requestId).map(AssistedActionDocumentVersionJpaEntity::toDomain);
    }
}

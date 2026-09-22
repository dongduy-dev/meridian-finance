package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.AssistedActionDocument;
import com.meridian.platform.document.domain.model.AssistedActionDocumentVersion;

import java.util.Optional;
import java.util.UUID;

public interface AssistedActionDocumentRepository {

    AssistedActionDocument saveDocument(AssistedActionDocument document);

    Optional<AssistedActionDocument> findOfferDocumentForUpdate(UUID loanApplicationId, UUID approvedOfferId);

    Optional<AssistedActionDocument> findOfferDocument(UUID loanApplicationId, UUID approvedOfferId);

    Optional<AssistedActionDocument> findContractDocumentForUpdate(
            UUID loanApplicationId, UUID loanContractId, int contractVersion);

    Optional<AssistedActionDocument> findContractDocument(
            UUID loanApplicationId, UUID loanContractId, int contractVersion);

    AssistedActionDocumentVersion saveVersion(AssistedActionDocumentVersion version);

    Optional<AssistedActionDocumentVersion> findVersionById(UUID versionId);

    Optional<AssistedActionDocumentVersion> findVersionByUploadRequestId(UUID uploadRequestId);
}

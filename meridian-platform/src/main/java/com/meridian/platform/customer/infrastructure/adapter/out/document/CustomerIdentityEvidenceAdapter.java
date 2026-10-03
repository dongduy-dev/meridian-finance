package com.meridian.platform.customer.infrastructure.adapter.out.document;

import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.document.application.port.in.CustomerIdentityEvidenceUseCase;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Component("customerVerificationDocumentEvidenceAdapter")
public class CustomerIdentityEvidenceAdapter implements CustomerIdentityEvidencePort {
    private final CustomerIdentityEvidenceUseCase documents;
    public CustomerIdentityEvidenceAdapter(CustomerIdentityEvidenceUseCase documents) { this.documents = documents; }
    public Evidence store(UUID customerId, UUID requestId, UUID baseline, InputStream content, String mime, String filename, UUID actor, LocalDateTime now) {
        return map(documents.store(customerId, requestId, baseline, content, mime, filename, actor, now));
    }
    public UUID lockIntakeCustomer(UUID caseId, boolean requireOpen) { return documents.lockIntakeCustomer(caseId, requireOpen); }
    public Evidence requireCurrent(UUID customerId, UUID caseId, UUID versionId) { return map(documents.requireCurrent(customerId, caseId, versionId)); }
    public Evidence metadata(UUID customerId, UUID caseId, UUID versionId) { return map(documents.metadata(customerId, caseId, versionId)); }
    public Content read(UUID customerId, UUID caseId, UUID versionId) {
        var v = documents.read(customerId, caseId, versionId);
        return new Content(v.filename(), v.mimeType(), v.byteSize(), v.content());
    }
    private static Evidence map(CustomerIdentityEvidenceUseCase.Evidence v) { return new Evidence(v.versionId(), v.versionNumber(), v.filename(), v.mimeType(), v.byteSize(), v.uploadedAt()); }
}

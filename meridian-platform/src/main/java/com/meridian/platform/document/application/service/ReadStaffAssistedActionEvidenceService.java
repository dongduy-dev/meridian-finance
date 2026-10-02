package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.dto.StaffAssistedActionEvidenceDto;
import com.meridian.platform.document.application.dto.StaffDocumentChecklistDto;
import com.meridian.platform.document.application.port.in.ReadStaffAssistedActionEvidenceUseCase;
import com.meridian.platform.document.application.port.out.AssistedActionDocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.AssistedActionDocument;
import com.meridian.platform.document.domain.model.AssistedActionDocumentVersion;
import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ReadStaffAssistedActionEvidenceService implements ReadStaffAssistedActionEvidenceUseCase {
    private final AssistedActionDocumentRepository documents;
    private final DocumentStoragePort storage;
    private final LoanDocumentWorkflowPort workflows;
    private final CurrentUserProvider currentUsers;

    public ReadStaffAssistedActionEvidenceService(AssistedActionDocumentRepository documents,
            DocumentStoragePort storage, LoanDocumentWorkflowPort workflows, CurrentUserProvider currentUsers) {
        this.documents = documents;
        this.storage = storage;
        this.workflows = workflows;
        this.currentUsers = currentUsers;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<StaffAssistedActionEvidenceDto> query(UUID loanApplicationId) {
        var actor = currentUsers.currentUser();
        if (Arrays.stream(AssistedActionEvidenceType.values()).noneMatch(type -> canRead(actor, type))) throw denied();
        workflows.find(loanApplicationId);
        return documents.findByLoanApplicationId(loanApplicationId).stream()
                .filter(document -> canRead(actor, document.evidenceType()))
                .map(document -> toDto(loanApplicationId, document)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentDto read(UUID loanApplicationId, AssistedActionEvidenceType evidenceType, UUID documentVersionId) {
        if (!canRead(currentUsers.currentUser(), evidenceType)) throw denied();
        var version = documents.findVersionById(documentVersionId).orElseThrow(
                ReadStaffAssistedActionEvidenceService::notFound);
        var document = documents.findDocumentById(version.assistedActionDocumentId())
                .filter(value -> value.loanApplicationId().equals(loanApplicationId) && value.evidenceType() == evidenceType)
                .orElseThrow(ReadStaffAssistedActionEvidenceService::notFound);
        if (!version.assistedActionDocumentId().equals(document.id())) throw notFound();
        return new DocumentContentDto(version.originalFilename(), version.detectedMimeType(), version.byteSize(),
                storage.open(version.storageKey()));
    }

    private StaffAssistedActionEvidenceDto toDto(UUID applicationId, AssistedActionDocument document) {
        var versions = documents.findVersionsByDocumentId(document.id());
        if (!applicationId.equals(document.loanApplicationId())
                || versions.stream().anyMatch(value -> !document.id().equals(value.assistedActionDocumentId()))) throw conflict();
        var current = document.currentVersionId() == null ? null : versions.stream()
                .filter(value -> value.id().equals(document.currentVersionId())).findFirst().orElseThrow(
                        ReadStaffAssistedActionEvidenceService::conflict);
        return new StaffAssistedActionEvidenceDto(document.id(), document.evidenceType().name(), document.approvedOfferId(),
                document.declaredOfferDecision() == null ? null : document.declaredOfferDecision().name(),
                document.loanContractId(), document.contractVersion(), document.correctionRequestId(),
                current == null ? null : toVersion(current), versions.stream().map(
                        ReadStaffAssistedActionEvidenceService::toVersion).toList());
    }

    private static StaffDocumentChecklistDto.VersionDto toVersion(AssistedActionDocumentVersion version) {
        return new StaffDocumentChecklistDto.VersionDto(version.id(), version.versionNumber(), version.originalFilename(),
                version.detectedMimeType(), version.byteSize(), version.uploadedAt());
    }

    private static boolean canRead(AuthenticatedUser actor, AssistedActionEvidenceType type) {
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()) return false;
        if (actor.hasPermission("document:review")) return true;
        return switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> actor.hasPermission("loan:offer:respond:staff") && actor.roles().contains("LOAN_OFFICER");
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> actor.hasPermission("loan:contract:read") && actor.roles().contains("ACCOUNTING_OFFICER");
            case CUSTOMER_CANCELLATION_REQUEST -> actor.hasPermission("loan:correction:staff") && actor.roles().contains("LOAN_OFFICER");
        };
    }

    private static AuthorizationException denied() {
        return new AuthorizationException("ASSISTED_ACTION_EVIDENCE_ACCESS_DENIED", "Signed Customer action evidence access is denied.");
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("DOCUMENT_VERSION_NOT_FOUND", "Signed Customer action evidence version was not found.");
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", "Signed Customer action evidence is inconsistent.");
    }
}

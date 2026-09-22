package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.*;
import com.meridian.platform.document.application.port.in.ManageAssistedActionEvidenceUseCase;
import com.meridian.platform.document.application.port.out.*;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssistedActionEvidenceService
        implements ManageAssistedActionEvidenceUseCase, LoanAssistedActionEvidencePort {

    private static final String PERMISSION = "document:upload:assisted-action";

    private final AssistedActionDocumentRepository documents;
    private final DocumentStoragePort storage;
    private final LoanAssistedActionAuthorizationPort authorizations;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public AssistedActionEvidenceService(
            AssistedActionDocumentRepository documents,
            DocumentStoragePort storage,
            LoanAssistedActionAuthorizationPort authorizations,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.documents = documents;
        this.storage = storage;
        this.authorizations = authorizations;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssistedActionEvidenceVersionDto upload(UploadAssistedActionEvidenceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        StagedDocument staged = storage.stage(
                command.content(), command.declaredMimeType(), command.originalFilename());
        try {
            return store(command, staged);
        } finally {
            storage.discardStaged(staged);
        }
    }

    protected AssistedActionEvidenceVersionDto store(
            UploadAssistedActionEvidenceCommand command, StagedDocument staged
    ) {
        requireCommand(command);
        AuthenticatedUser actor = requireActor(command.evidenceType());
        authorizeTarget(command);
        LocalDateTime now = LocalDateTime.now(clock);
        AssistedActionDocument document = lockOrCreate(command, now);
        requireSameTarget(document, command);

        AssistedActionDocumentVersion replay = documents
                .findVersionByUploadRequestId(command.uploadRequestId()).orElse(null);
        if (replay != null) {
            if (!replay.sameLogicalUpload(
                    document.id(), command.expectedCurrentVersionId(), staged.originalFilename(),
                    staged.declaredMimeType(), staged.byteSize(), staged.sha256Hex(), actor.userId())) {
                throw new BusinessStateConflictException(
                        "IDEMPOTENCY_KEY_REUSED", "The request ID was already used for different evidence.");
            }
            return toVersionDto(replay);
        }
        if (!Objects.equals(document.currentVersionId(), command.expectedCurrentVersionId())) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION", "The assisted-action evidence current version changed.");
        }
        int versionNumber = document.currentVersionId() == null ? 1
                : documents.findVersionById(document.currentVersionId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Current assisted-action evidence version was not found."))
                        .versionNumber() + 1;

        StoredObject stored = storage.commit(staged);
        registerRollbackCleanup(stored.storageKey());
        AssistedActionDocumentVersion version = documents.saveVersion(new AssistedActionDocumentVersion(
                UUID.randomUUID(), document.id(), versionNumber, command.uploadRequestId(),
                command.expectedCurrentVersionId(), staged.originalFilename(), staged.declaredMimeType(),
                staged.detectedMimeType(), staged.byteSize(), staged.sha256Hex(), stored.storageKey(),
                actor.userId(), now));
        documents.saveDocument(document.withCurrentVersion(version.id(), now));
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(command.uploadRequestId(), actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.ASSISTED_ACTION_DOCUMENT_VERSION_UPLOADED,
                        BusinessAuditEntityType.ASSISTED_ACTION_DOCUMENT_VERSION,
                        version.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, command.loanApplicationId())
                                .put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, version.id())
                                .put(BusinessAuditPayloadKey.DOCUMENT_TYPE, command.evidenceType())
                                .build())));
        return toVersionDto(version);
    }

    @Override
    @Transactional
    public EvidenceSnapshot requireCurrentOfferEvidence(
            UUID loanApplicationId, UUID approvedOfferId, String decision, UUID documentVersionId
    ) {
        AssistedActionDocument document = documents.findOfferDocumentForUpdate(loanApplicationId, approvedOfferId)
                .orElseThrow(AssistedActionEvidenceService::evidenceRequired);
        if (document.evidenceType() != AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE
                || document.declaredOfferDecision() != AssistedOfferDecision.valueOf(decision)) {
            throw evidenceInvalid();
        }
        return requireVersion(document, documentVersionId);
    }

    @Override
    @Transactional
    public EvidenceSnapshot requireCurrentContractEvidence(
            UUID loanApplicationId, UUID loanContractId, int contractVersion, UUID documentVersionId
    ) {
        AssistedActionDocument document = documents
                .findContractDocumentForUpdate(loanApplicationId, loanContractId, contractVersion)
                .orElseThrow(AssistedActionEvidenceService::evidenceRequired);
        if (document.evidenceType() != AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT) {
            throw evidenceInvalid();
        }
        return requireVersion(document, documentVersionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceSnapshot> findOfferEvidence(UUID loanApplicationId, UUID approvedOfferId) {
        return documents.findOfferDocument(loanApplicationId, approvedOfferId).flatMap(this::currentSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceSnapshot> findContractEvidence(
            UUID loanApplicationId, UUID loanContractId, int contractVersion
    ) {
        return documents.findContractDocument(loanApplicationId, loanContractId, contractVersion)
                .flatMap(this::currentSnapshot);
    }

    private AssistedActionDocument lockOrCreate(
            UploadAssistedActionEvidenceCommand command, LocalDateTime now
    ) {
        Optional<AssistedActionDocument> existing = command.evidenceType()
                == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE
                ? documents.findOfferDocumentForUpdate(command.loanApplicationId(), command.approvedOfferId())
                : documents.findContractDocumentForUpdate(
                        command.loanApplicationId(), command.loanContractId(), command.contractVersion());
        return existing.orElseGet(() -> documents.saveDocument(new AssistedActionDocument(
                UUID.randomUUID(), command.loanApplicationId(), command.evidenceType(),
                command.approvedOfferId(), command.declaredOfferDecision(), command.loanContractId(),
                command.contractVersion(), null, now, now)));
    }

    private void authorizeTarget(UploadAssistedActionEvidenceCommand command) {
        if (command.evidenceType() == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE) {
            authorizations.authorizeOfferEvidence(command.loanApplicationId(), command.approvedOfferId(),
                    command.declaredOfferDecision().name());
        } else {
            authorizations.authorizeContractEvidence(command.loanApplicationId(), command.loanContractId(),
                    command.contractVersion());
        }
    }

    private static void requireSameTarget(
            AssistedActionDocument document, UploadAssistedActionEvidenceCommand command
    ) {
        if (document.evidenceType() != command.evidenceType()
                || !Objects.equals(document.approvedOfferId(), command.approvedOfferId())
                || document.declaredOfferDecision() != command.declaredOfferDecision()
                || !Objects.equals(document.loanContractId(), command.loanContractId())
                || !Objects.equals(document.contractVersion(), command.contractVersion())) {
            throw evidenceInvalid();
        }
    }

    private EvidenceSnapshot requireVersion(AssistedActionDocument document, UUID documentVersionId) {
        if (document.currentVersionId() == null || !document.currentVersionId().equals(documentVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION", "The supplied evidence version is not the current version.");
        }
        AssistedActionDocumentVersion version = documents.findVersionById(documentVersionId)
                .orElseThrow(AssistedActionEvidenceService::evidenceRequired);
        if (!version.assistedActionDocumentId().equals(document.id())) throw evidenceInvalid();
        return snapshot(document, version);
    }

    private Optional<EvidenceSnapshot> currentSnapshot(AssistedActionDocument document) {
        if (document.currentVersionId() == null) return Optional.empty();
        return documents.findVersionById(document.currentVersionId())
                .filter(version -> version.assistedActionDocumentId().equals(document.id()))
                .map(version -> snapshot(document, version));
    }

    private static EvidenceSnapshot snapshot(
            AssistedActionDocument document, AssistedActionDocumentVersion version
    ) {
        return new EvidenceSnapshot(
                document.id(), version.id(), document.evidenceType().name(), document.approvedOfferId(),
                document.declaredOfferDecision() == null ? null : document.declaredOfferDecision().name(),
                document.loanContractId(), document.contractVersion(), version.versionNumber(),
                version.detectedMimeType(), version.byteSize(), version.uploadedAt());
    }

    private AuthenticatedUser requireActor(AssistedActionEvidenceType type) {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException("ASSISTED_ACTION_EVIDENCE_ACCESS_DENIED",
                    "Staff-assisted action evidence access is denied.");
        }
        String requiredRole = type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE
                ? "LOAN_OFFICER" : "ACCOUNTING_OFFICER";
        if (!actor.roles().contains(requiredRole)) {
            throw new AuthorizationException("ASSISTED_ACTION_ROLE_REQUIRED",
                    "The required Staff business role is missing.");
        }
        return actor;
    }

    private static void requireCommand(UploadAssistedActionEvidenceCommand command) {
        Objects.requireNonNull(command.loanApplicationId());
        Objects.requireNonNull(command.evidenceType());
        Objects.requireNonNull(command.uploadRequestId());
        if (command.evidenceType() == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE) {
            Objects.requireNonNull(command.approvedOfferId());
            Objects.requireNonNull(command.declaredOfferDecision());
            if (command.loanContractId() != null || command.contractVersion() != null) {
                throw new IllegalArgumentException("Offer evidence cannot target a contract.");
            }
        } else {
            Objects.requireNonNull(command.loanContractId());
            if (command.contractVersion() == null || command.contractVersion() <= 0) {
                throw new IllegalArgumentException("contractVersion must be positive");
            }
            if (command.approvedOfferId() != null || command.declaredOfferDecision() != null) {
                throw new IllegalArgumentException("Contract evidence cannot target an offer.");
            }
        }
    }

    private static AssistedActionEvidenceVersionDto toVersionDto(AssistedActionDocumentVersion version) {
        return new AssistedActionEvidenceVersionDto(
                version.id(), version.versionNumber(), version.detectedMimeType(),
                version.byteSize(), version.uploadedAt());
    }

    private static BusinessRuleViolationException evidenceRequired() {
        return new BusinessRuleViolationException(
                "ASSISTED_ACTION_EVIDENCE_REQUIRED", "Current signed assisted-action evidence is required.");
    }

    private static BusinessStateConflictException evidenceInvalid() {
        return new BusinessStateConflictException(
                "ASSISTED_ACTION_EVIDENCE_INVALID", "Assisted-action evidence does not match the business target.");
    }

    private void registerRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) storage.deleteFinal(storageKey);
            }
        });
    }
}

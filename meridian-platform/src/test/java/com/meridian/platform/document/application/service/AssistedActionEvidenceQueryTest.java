package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.in.AssistedActionEvidenceUseCase;
import com.meridian.platform.document.application.port.out.AssistedActionDocumentRepository;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.loan.infrastructure.adapter.out.document.LoanAssistedActionEvidenceAdapter;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistedActionEvidenceQueryTest {

    private final AssistedActionDocumentRepository repository = mock(AssistedActionDocumentRepository.class);
    private final AssistedActionEvidenceService service = new AssistedActionEvidenceService(
            repository, null, null, null, null, null, Clock.systemUTC());
    private final LoanAssistedActionEvidenceAdapter adapter = new LoanAssistedActionEvidenceAdapter(service);
    private final UUID application = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @Test
    void offerDecisionAndEvidenceCategoryCannotBeRelabeled() {
        var offer = document(AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE);
        when(repository.findOfferDocumentForUpdate(application, target)).thenReturn(Optional.of(offer));
        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", assertThrows(BusinessStateConflictException.class,
                () -> adapter.requireCurrentOfferEvidence(application, target, "DECLINE", versionId)).getErrorCode());
        when(repository.findContractDocumentForUpdate(application, target, 7)).thenReturn(Optional.of(offer));
        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", assertThrows(BusinessStateConflictException.class,
                () -> adapter.requireCurrentContractEvidence(application, target, 7, versionId)).getErrorCode());
        when(repository.findCancellationDocumentForUpdate(application, target)).thenReturn(Optional.of(offer));
        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", assertThrows(BusinessStateConflictException.class,
                () -> adapter.requireCurrentCancellationEvidence(application, target, versionId)).getErrorCode());
        verify(repository, never()).findVersionById(any());
    }

    @ParameterizedTest
    @EnumSource(AssistedActionEvidenceType.class)
    void lockedProofAndReadQueryKeepExactTargetsAndVersionFacts(AssistedActionEvidenceType type) {
        var document = document(type);
        stubLocked(type, Optional.of(document));
        stubRead(type, Optional.of(document));
        var version = version(document.id());
        when(repository.findVersionById(versionId)).thenReturn(Optional.of(version));

        var required = require(type, versionId);
        var found = find(type).orElseThrow();

        assertEquals(required, found);
        assertEquals(document.id(), required.documentId());
        assertEquals(versionId, required.documentVersionId());
        assertEquals(type.name(), required.evidenceType());
        assertEquals(document.approvedOfferId(), required.approvedOfferId());
        assertEquals(document.declaredOfferDecision() == null ? null : "ACCEPT", required.declaredOfferDecision());
        assertEquals(document.loanContractId(), required.loanContractId());
        assertEquals(document.contractVersion(), required.contractVersion());
        assertEquals(document.correctionRequestId(), required.correctionRequestId());
        assertEquals(4, required.versionNumber());
        assertEquals("application/pdf", required.detectedMimeType());
        assertEquals(123, required.byteSize());
        assertEquals(version.uploadedAt(), required.uploadedAt());
        verifyLookups(type);
        verify(repository, times(2)).findVersionById(versionId);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(AssistedActionEvidenceType.class)
    void staleMissingAndForeignVersionProofFailWithoutEvidenceFallback(AssistedActionEvidenceType type) {
        var document = document(type);
        stubLocked(type, Optional.of(document));
        assertEquals("STALE_DOCUMENT_VERSION", assertThrows(BusinessStateConflictException.class,
                () -> require(type, UUID.randomUUID())).getErrorCode());
        when(repository.findVersionById(versionId)).thenReturn(Optional.empty());
        assertEquals("ASSISTED_ACTION_EVIDENCE_REQUIRED", assertThrows(BusinessRuleViolationException.class,
                () -> require(type, versionId)).getErrorCode());
        when(repository.findVersionById(versionId)).thenReturn(Optional.of(version(UUID.randomUUID())));
        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", assertThrows(BusinessStateConflictException.class,
                () -> require(type, versionId)).getErrorCode());
        stubRead(type, Optional.of(document));
        assertTrue(find(type).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(AssistedActionEvidenceType.class)
    void missingEvidenceRemainsEmptyForReadsAndRequiredForProof(AssistedActionEvidenceType type) {
        stubLocked(type, Optional.empty());
        stubRead(type, Optional.empty());
        assertTrue(find(type).isEmpty());
        assertEquals("ASSISTED_ACTION_EVIDENCE_REQUIRED", assertThrows(BusinessRuleViolationException.class,
                () -> require(type, versionId)).getErrorCode());
        verify(repository, never()).findVersionById(any());
    }

    @ParameterizedTest
    @EnumSource(AssistedActionEvidenceType.class)
    void owningServiceRetainsRequiredAndReadOnlyTransactionDefinitions(AssistedActionEvidenceType type) throws Exception {
        String suffix = switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> "OfferEvidence";
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> "ContractEvidence";
            case CUSTOMER_CANCELLATION_REQUEST -> "CancellationEvidence";
        };
        for (var method : AssistedActionEvidenceUseCase.class.getDeclaredMethods()) {
            if (!method.getName().endsWith(suffix)) continue;
            var transaction = AssistedActionEvidenceService.class.getMethod(method.getName(), method.getParameterTypes())
                    .getAnnotation(Transactional.class);
            assertNotNull(transaction);
            assertEquals(Propagation.REQUIRED, transaction.propagation());
            assertEquals(method.getName().startsWith("find"), transaction.readOnly());
        }
        assertNull(LoanAssistedActionEvidenceAdapter.class.getAnnotation(Transactional.class));
        for (var method : LoanAssistedActionEvidenceAdapter.class.getDeclaredMethods())
            assertNull(method.getAnnotation(Transactional.class));
    }

    private AssistedActionDocument document(AssistedActionEvidenceType type) {
        return new AssistedActionDocument(UUID.randomUUID(), application, type,
                type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? target : null,
                type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? AssistedOfferDecision.ACCEPT : null,
                type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? target : null,
                type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? 7 : null,
                type == AssistedActionEvidenceType.CUSTOMER_CANCELLATION_REQUEST ? target : null,
                versionId, LocalDateTime.MIN, LocalDateTime.MIN);
    }

    private AssistedActionDocumentVersion version(UUID document) {
        return new AssistedActionDocumentVersion(versionId, document, 4, UUID.randomUUID(), null,
                "signed.pdf", "application/pdf", "application/pdf", 123, "a".repeat(64),
                "private-test-object", UUID.randomUUID(), LocalDateTime.of(2026, 10, 27, 8, 0));
    }

    private void stubLocked(AssistedActionEvidenceType type, Optional<AssistedActionDocument> result) {
        switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> when(repository.findOfferDocumentForUpdate(application, target)).thenReturn(result);
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> when(repository.findContractDocumentForUpdate(application, target, 7)).thenReturn(result);
            case CUSTOMER_CANCELLATION_REQUEST -> when(repository.findCancellationDocumentForUpdate(application, target)).thenReturn(result);
        }
    }

    private void stubRead(AssistedActionEvidenceType type, Optional<AssistedActionDocument> result) {
        switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> when(repository.findOfferDocument(application, target)).thenReturn(result);
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> when(repository.findContractDocument(application, target, 7)).thenReturn(result);
            case CUSTOMER_CANCELLATION_REQUEST -> when(repository.findCancellationDocument(application, target)).thenReturn(result);
        }
    }

    private com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort.EvidenceSnapshot require(
            AssistedActionEvidenceType type, UUID version
    ) {
        return switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> adapter.requireCurrentOfferEvidence(application, target, "ACCEPT", version);
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> adapter.requireCurrentContractEvidence(application, target, 7, version);
            case CUSTOMER_CANCELLATION_REQUEST -> adapter.requireCurrentCancellationEvidence(application, target, version);
        };
    }

    private Optional<com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort.EvidenceSnapshot> find(
            AssistedActionEvidenceType type
    ) {
        return switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> adapter.findOfferEvidence(application, target);
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> adapter.findContractEvidence(application, target, 7);
            case CUSTOMER_CANCELLATION_REQUEST -> adapter.findCancellationEvidence(application, target);
        };
    }

    private void verifyLookups(AssistedActionEvidenceType type) {
        switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> {
                verify(repository).findOfferDocumentForUpdate(application, target);
                verify(repository).findOfferDocument(application, target);
            }
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> {
                verify(repository).findContractDocumentForUpdate(application, target, 7);
                verify(repository).findContractDocument(application, target, 7);
            }
            case CUSTOMER_CANCELLATION_REQUEST -> {
                verify(repository).findCancellationDocumentForUpdate(application, target);
                verify(repository).findCancellationDocument(application, target);
            }
        }
    }
}

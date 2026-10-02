package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.*;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadStaffAssistedActionEvidenceServiceTest {
    @Mock AssistedActionDocumentRepository documents;
    @Mock DocumentStoragePort storage;
    @Mock LoanDocumentWorkflowPort workflows;
    @Mock CurrentUserProvider users;
    ReadStaffAssistedActionEvidenceService service;
    final UUID applicationId = UUID.randomUUID();
    final LocalDateTime now = LocalDateTime.of(2026, 10, 3, 9, 0);

    @BeforeEach void setup() {
        service = new ReadStaffAssistedActionEvidenceService(documents, storage, workflows, users);
    }

    @ParameterizedTest @EnumSource(AssistedActionEvidenceType.class)
    void readsOrderedMetadataAndActualHistoricalBytesWithoutWrites(AssistedActionEvidenceType type) throws Exception {
        when(users.currentUser()).thenReturn(staff("document:review", Set.of()));
        var document = document(type);
        var first = version(document, 1);
        var second = version(document, 2);
        document = document.withCurrentVersion(second.id(), now);
        when(documents.findByLoanApplicationId(applicationId)).thenReturn(List.of(document));
        when(documents.findVersionsByDocumentId(document.id())).thenReturn(List.of(first, second));
        var result = service.query(applicationId).getFirst();
        assertEquals(List.of(1, 2), result.versionHistory().stream().map(value -> value.versionNumber()).toList());
        assertEquals(second.id(), result.currentVersion().documentVersionId());
        assertEquals(document.approvedOfferId(), result.approvedOfferId());
        assertEquals(document.loanContractId(), result.loanContractId());
        assertEquals(document.correctionRequestId(), result.correctionRequestId());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(result);
        assertFalse(json.contains("storageKey"));
        assertFalse(json.contains("uploadRequestId"));
        assertFalse(json.contains("sha256"));
        when(documents.findVersionById(first.id())).thenReturn(Optional.of(first));
        when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));
        when(storage.open(first.storageKey())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        var content = service.read(applicationId, type, first.id());
        assertArrayEquals(new byte[]{1, 2, 3}, content.content().readAllBytes());
        verify(documents, never()).saveDocument(any());
        verify(documents, never()).saveVersion(any());
        verify(storage, never()).commit(any());
    }

    @ParameterizedTest @EnumSource(AssistedActionEvidenceType.class)
    void requiresExactPurposeAndBusinessRoleAndFiltersOtherTypes(AssistedActionEvidenceType type) {
        String permission = permission(type);
        String role = type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? "ACCOUNTING_OFFICER" : "LOAN_OFFICER";
        when(users.currentUser()).thenReturn(staff(permission, Set.of()));
        assertThrows(AuthorizationException.class, () -> service.read(applicationId, type, UUID.randomUUID()));
        when(users.currentUser()).thenReturn(staff(permission, Set.of(role)));
        var document = document(type);
        var version = version(document, 1);
        when(documents.findByLoanApplicationId(applicationId)).thenReturn(Arrays.stream(AssistedActionEvidenceType.values())
                .map(this::document).toList());
        var items = service.query(applicationId);
        assertEquals(List.of(type.name()), items.stream().map(value -> value.evidenceType()).toList());
        when(documents.findVersionById(version.id())).thenReturn(Optional.of(version));
        when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));
        service.read(applicationId, type, version.id());
    }

    @ParameterizedTest @ValueSource(strings = {"loan:read", "audit:read", "admin:config", "document:read", "document:review:extra", "approval:decide", "document:upload:assisted-action"})
    void deniesUnrelatedPermission(String permission) {
        when(users.currentUser()).thenReturn(staff(permission, Set.of("LOAN_OFFICER", "ACCOUNTING_OFFICER")));
        assertThrows(AuthorizationException.class, () -> service.query(applicationId));
        verifyNoInteractions(documents, storage, workflows);
    }

    @Test void deniesCustomerEvenWithStaffPermissions() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "customer@meridian.test",
                "CUSTOMER", UUID.randomUUID(), Set.of("LOAN_OFFICER"), Set.of("document:review")));
        assertThrows(AuthorizationException.class, () -> service.query(applicationId));
        assertThrows(AuthorizationException.class, () -> service.read(applicationId, AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE, UUID.randomUUID()));
        verifyNoInteractions(documents, storage, workflows);
    }

    @ParameterizedTest @EnumSource(AssistedActionEvidenceType.class)
    void rejectsApplicationOrEvidenceTypeMismatchBeforeOpeningStorage(AssistedActionEvidenceType type) {
        when(users.currentUser()).thenReturn(staff("document:review", Set.of()));
        var document = document(type);
        var version = version(document, 1);
        when(documents.findVersionById(version.id())).thenReturn(Optional.of(version));
        when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));
        assertThrows(EntityNotFoundException.class, () -> service.read(UUID.randomUUID(), type, version.id()));
        var otherType = Arrays.stream(AssistedActionEvidenceType.values()).filter(value -> value != type).findFirst().orElseThrow();
        assertThrows(EntityNotFoundException.class, () -> service.read(applicationId, otherType, version.id()));
        verifyNoInteractions(storage);
    }

    private AssistedActionDocument document(AssistedActionEvidenceType type) {
        return new AssistedActionDocument(UUID.randomUUID(), applicationId, type,
                type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? UUID.randomUUID() : null,
                type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? AssistedOfferDecision.ACCEPT : null,
                type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? UUID.randomUUID() : null,
                type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? 1 : null,
                type == AssistedActionEvidenceType.CUSTOMER_CANCELLATION_REQUEST ? UUID.randomUUID() : null,
                null, now, now);
    }

    private AssistedActionDocumentVersion version(AssistedActionDocument document, int number) {
        return new AssistedActionDocumentVersion(UUID.randomUUID(), document.id(), number, UUID.randomUUID(), null,
                "signed.pdf", "application/pdf", "application/pdf", 3, "a".repeat(64), "private/" + number,
                UUID.fromString("00000000-0000-0000-0000-000000000302"), now);
    }

    private static AuthenticatedUser staff(String permission, Set<String> roles) {
        return new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.test", "STAFF", null, roles, Set.of(permission));
    }

    private static String permission(AssistedActionEvidenceType type) {
        return switch (type) {
            case CUSTOMER_OFFER_RESPONSE -> "loan:offer:respond:staff";
            case CUSTOMER_CONTRACT_ACKNOWLEDGMENT -> "loan:contract:read";
            case CUSTOMER_CANCELLATION_REQUEST -> "loan:correction:staff";
        };
    }
}

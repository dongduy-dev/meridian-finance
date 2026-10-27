package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.AssistedActionEvidenceUseCase;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanAssistedActionEvidenceAdapterTest {

    private final AssistedActionEvidenceUseCase provider = mock(AssistedActionEvidenceUseCase.class);
    private final LoanAssistedActionEvidenceAdapter adapter = new LoanAssistedActionEvidenceAdapter(provider);
    private final UUID application = UUID.randomUUID();
    private final UUID offer = UUID.randomUUID();
    private final UUID contract = UUID.randomUUID();
    private final UUID correction = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();

    @Test
    void mapsAllFieldsForAllSixMethodsAndKeepsExactTargets() {
        for (boolean populated : List.of(true, false)) {
            var source = new AssistedActionEvidenceUseCase.EvidenceSnapshot(UUID.randomUUID(), version,
                    "CUSTOMER_OFFER_RESPONSE", populated ? offer : null, populated ? "DECLINE" : null,
                    populated ? contract : null, populated ? 7 : null, populated ? correction : null,
                    3, "application/pdf", 10689L, LocalDateTime.of(2026, 10, 27, 12, 34, 56, 123000000));
            var expected = new LoanAssistedActionEvidencePort.EvidenceSnapshot(source.documentId(), version,
                    source.evidenceType(), source.approvedOfferId(), source.declaredOfferDecision(),
                    source.loanContractId(), source.contractVersion(), source.correctionRequestId(),
                    3, "application/pdf", 10689L, source.uploadedAt());
            when(provider.requireCurrentOfferEvidence(application, offer, "DECLINE", version)).thenReturn(source);
            when(provider.requireCurrentContractEvidence(application, contract, 7, version)).thenReturn(source);
            when(provider.requireCurrentCancellationEvidence(application, correction, version)).thenReturn(source);
            when(provider.findOfferEvidence(application, offer)).thenReturn(Optional.of(source));
            when(provider.findContractEvidence(application, contract, 7)).thenReturn(Optional.of(source));
            when(provider.findCancellationEvidence(application, correction)).thenReturn(Optional.of(source));

            assertEquals(expected, adapter.requireCurrentOfferEvidence(application, offer, "DECLINE", version));
            assertEquals(expected, adapter.requireCurrentContractEvidence(application, contract, 7, version));
            assertEquals(expected, adapter.requireCurrentCancellationEvidence(application, correction, version));
            assertEquals(Optional.of(expected), adapter.findOfferEvidence(application, offer));
            assertEquals(Optional.of(expected), adapter.findContractEvidence(application, contract, 7));
            assertEquals(Optional.of(expected), adapter.findCancellationEvidence(application, correction));
        }
        verify(provider, times(2)).requireCurrentOfferEvidence(application, offer, "DECLINE", version);
        verify(provider, times(2)).requireCurrentContractEvidence(application, contract, 7, version);
        verify(provider, times(2)).requireCurrentCancellationEvidence(application, correction, version);
        verify(provider, times(2)).findOfferEvidence(application, offer);
        verify(provider, times(2)).findContractEvidence(application, contract, 7);
        verify(provider, times(2)).findCancellationEvidence(application, correction);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void preservesEmptyOptionalsWithoutLookingUpDifferentEvidence() {
        when(provider.findOfferEvidence(application, offer)).thenReturn(Optional.empty());
        when(provider.findContractEvidence(application, contract, 7)).thenReturn(Optional.empty());
        when(provider.findCancellationEvidence(application, correction)).thenReturn(Optional.empty());
        assertTrue(adapter.findOfferEvidence(application, offer).isEmpty());
        assertTrue(adapter.findContractEvidence(application, contract, 7).isEmpty());
        assertTrue(adapter.findCancellationEvidence(application, correction).isEmpty());
        verify(provider).findOfferEvidence(application, offer);
        verify(provider).findContractEvidence(application, contract, 7);
        verify(provider).findCancellationEvidence(application, correction);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void preservesInvalidAndStaleFailureIdentityOnAllMethods() {
        for (String code : List.of("ASSISTED_ACTION_EVIDENCE_INVALID", "STALE_DOCUMENT_VERSION")) {
            var failure = new BusinessStateConflictException(code, "Exact provider message.");
            var failing = new LoanAssistedActionEvidenceAdapter(mock(AssistedActionEvidenceUseCase.class, call -> { throw failure; }));
            List<Executable> calls = List.of(
                    () -> failing.requireCurrentOfferEvidence(application, offer, "ACCEPT", version),
                    () -> failing.requireCurrentContractEvidence(application, contract, 7, version),
                    () -> failing.requireCurrentCancellationEvidence(application, correction, version),
                    () -> failing.findOfferEvidence(application, offer),
                    () -> failing.findContractEvidence(application, contract, 7),
                    () -> failing.findCancellationEvidence(application, correction));
            for (var call : calls) assertSame(failure, assertThrows(BusinessStateConflictException.class, call));
        }
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.loan.application.port.in.AuthorizeAssistedActionEvidenceUseCase;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistedActionEvidenceAuthorizationAdapterTest {
    private final UUID app = UUID.randomUUID(), offer = UUID.randomUUID(), contract = UUID.randomUUID(), correction = UUID.randomUUID();
    @Test void forwardsEveryExactTargetVersionAndDecision() {
        var input = mock(AuthorizeAssistedActionEvidenceUseCase.class);
        var adapter = new AssistedActionEvidenceAuthorizationAdapter(input);
        adapter.authorizeOfferEvidence(app, offer, "ACCEPT");
        adapter.authorizeOfferEvidence(app, offer, "DECLINE");
        adapter.authorizeContractEvidence(app, contract, 7);
        adapter.authorizeCancellationEvidence(app, correction);
        verify(input).authorizeOfferEvidence(app, offer, "ACCEPT");
        verify(input).authorizeOfferEvidence(app, offer, "DECLINE");
        verify(input).authorizeContractEvidence(app, contract, 7);
        verify(input).authorizeCancellationEvidence(app, correction);
        verifyNoMoreInteractions(input);
    }
    @Test void propagatesFailuresWithoutAddingTargetValidation() {
        var failure = new IllegalArgumentException("provider validation");
        var input = mock(AuthorizeAssistedActionEvidenceUseCase.class, invocation -> { throw failure; });
        var adapter = new AssistedActionEvidenceAuthorizationAdapter(input);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> adapter.authorizeOfferEvidence(app, offer, "unknown")));
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> adapter.authorizeContractEvidence(app, contract, 0)));
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> adapter.authorizeCancellationEvidence(app, correction)));
    }
}

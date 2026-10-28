package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.loan.application.port.in.AuthorizeAssistedOriginationEvidenceUseCase;
import com.meridian.platform.document.application.spi.loan.LoanAssistedOriginationSpi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistedOriginationEvidenceAuthorizationAdapterTest {
    @ParameterizedTest @ValueSource(strings = {"UNSECURED_CONSUMER_LOAN", "COLLATERAL_LOAN"})
    void keepsReadMutationAndBothHistoricalIdentityFlagsDistinct(String product) {
        var input = mock(AuthorizeAssistedOriginationEvidenceUseCase.class);
        UUID id = UUID.randomUUID(), customer = UUID.randomUUID();
        var facts = new AuthorizeAssistedOriginationEvidenceUseCase.AuthorizedAssistedOrigination(id, product);
        when(input.authorizeEvidenceRead(id)).thenReturn(facts);
        when(input.authorizeEvidenceMutation(id)).thenReturn(facts);
        when(input.authorizeIdentityVerification(id, true)).thenReturn(customer);
        when(input.authorizeIdentityVerification(id, false)).thenReturn(customer);
        var adapter = new AssistedOriginationEvidenceAuthorizationAdapter(input);
        assertEquals(new LoanAssistedOriginationSpi.AuthorizedIntake(id, product), adapter.authorizeRead(id));
        verify(input, never()).authorizeEvidenceMutation(any());
        assertEquals(new LoanAssistedOriginationSpi.AuthorizedIntake(id, product), adapter.authorizeMutation(id));
        assertEquals(customer, adapter.authorizeIdentityVerification(id, true));
        assertEquals(customer, adapter.authorizeIdentityVerification(id, false));
        verify(input).authorizeEvidenceRead(id); verify(input).authorizeEvidenceMutation(id);
        verify(input).authorizeIdentityVerification(id, true); verify(input).authorizeIdentityVerification(id, false);
        verifyNoMoreInteractions(input);
    }
    @Test void everyOperationPropagatesTheExactAuthorityOrStateFailure() {
        var failure = new IllegalStateException("provider authority failure");
        var input = mock(AuthorizeAssistedOriginationEvidenceUseCase.class, invocation -> { throw failure; });
        var adapter = new AssistedOriginationEvidenceAuthorizationAdapter(input);
        UUID id = UUID.randomUUID();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeRead(id)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeMutation(id)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeIdentityVerification(id, true)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeIdentityVerification(id, false)));
    }
}

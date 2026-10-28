package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.loan.application.port.in.AuthorizeDocumentCorrectionUploadUseCase;
import com.meridian.platform.document.application.spi.loan.LoanDocumentCorrectionSpi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanDocumentCorrectionAdapterTest {
    private final UUID app = UUID.randomUUID(), item = UUID.randomUUID(), version = UUID.randomUUID();
    @ParameterizedTest @EnumSource(AuthorizeDocumentCorrectionUploadUseCase.StaffUploadAuthority.class)
    void mapsBothStaffAuthoritiesAndForwardsExactCustomerAndStaffTargets(AuthorizeDocumentCorrectionUploadUseCase.StaffUploadAuthority authority) {
        var input = mock(AuthorizeDocumentCorrectionUploadUseCase.class);
        when(input.authorizeStaffUpload(app, item, version)).thenReturn(authority);
        var adapter = new LoanDocumentCorrectionAdapter(input);
        adapter.authorizeCustomerUpload(app, item, version);
        assertEquals(LoanDocumentCorrectionSpi.StaffUploadAuthority.valueOf(authority.name()), adapter.authorizeStaffUpload(app, item, version));
        verify(input).authorizeCustomerUpload(app, item, version);
        verify(input).authorizeStaffUpload(app, item, version);
        verifyNoMoreInteractions(input);
    }
    @Test void preservesNullBaselineAndExactFailureIdentity() {
        var failure = new IllegalStateException("authorization failure");
        var input = mock(AuthorizeDocumentCorrectionUploadUseCase.class, invocation -> { throw failure; });
        var adapter = new LoanDocumentCorrectionAdapter(input);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeCustomerUpload(app, item, null)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.authorizeStaffUpload(app, item, null)));
        verify(input).authorizeCustomerUpload(app, item, null);
        verify(input).authorizeStaffUpload(app, item, null);
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.loan.application.service.PreReviewDocumentCorrectionService;
import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanDocumentReviewCorrectionAdapterTest {
    private final UUID app = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID baseline = UUID.randomUUID();
    private final BusinessOperationContext operation = BusinessOperationContext.user(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.of(2026, 10, 27, 12, 0));

    @ParameterizedTest
    @EnumSource(LoanCorrectionReasonCode.class)
    void exactCodesAndUnmodifiedEvidenceArgumentsReachLoanValidation(LoanCorrectionReasonCode reason) {
        var service = mock(PreReviewDocumentCorrectionService.class);
        new LoanDocumentReviewCorrectionAdapter(service).requestCustomerReplacement(app, item, baseline,
                reason.name(), "  Replace exactly  ", operation);
        verify(service).requestReplacement(app, item, baseline, reason, "  Replace exactly  ", operation);
        verifyNoMoreInteractions(service);
    }

    @Test
    void nullReasonAndLoanFailureAreNotHiddenByTheBoundary() {
        var service = mock(PreReviewDocumentCorrectionService.class);
        var failure = new IllegalStateException("mandatory correction failure");
        doThrow(failure).when(service).requestReplacement(app, item, baseline, null, "Replace", operation);
        var adapter = new LoanDocumentReviewCorrectionAdapter(service);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> adapter.requestCustomerReplacement(app, item, baseline, null, "Replace", operation)));
    }
}

package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollateralLoanReviewGateTest {

    private final UUID applicationId = UUID.randomUUID();
    private final LoanApplication application = new LoanApplication(
            applicationId, UUID.randomUUID(), UUID.randomUUID(), "CL-20260819-000001",
            ProductCode.COLLATERAL_LOAN, ProductType.SECURED, LoanApplicationStatus.SUBMITTED,
            new BigDecimal("15000000"), 12, LocalDateTime.of(2026, 8, 19, 8, 0)
    );
    private final CollateralLoanVerificationRepository repository =
            mock(CollateralLoanVerificationRepository.class);
    private final CollateralLoanReviewGate gate = new CollateralLoanReviewGate(repository);

    @Test
    void missingVerificationFailsClosed() {
        when(repository.findByLoanApplicationId(applicationId)).thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> gate.requireProgressionAllowed(application)
        );

        assertEquals("COLLATERAL_VERIFICATION_REQUIRED", exception.getErrorCode());
    }

    @Test
    void everyNonVerifiedResultFailsWithTruthfulCode() {
        assertBlocked(pending(), "PRODUCT_VERIFICATION_PENDING");
        assertBlocked(completed(CollateralLoanManualVerificationOutcome.FAILED),
                "PRODUCT_VERIFICATION_FAILED");
        assertBlocked(completed(CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION),
                "PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION");
    }

    @Test
    void verifiedResultAllowsReviewProgression() {
        when(repository.findByLoanApplicationId(applicationId))
                .thenReturn(Optional.of(completed(CollateralLoanManualVerificationOutcome.VERIFIED)));

        assertDoesNotThrow(() -> gate.requireProgressionAllowed(application));
    }

    private void assertBlocked(CollateralLoanVerification verification, String errorCode) {
        when(repository.findByLoanApplicationId(applicationId)).thenReturn(Optional.of(verification));
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> gate.requireProgressionAllowed(application)
        );
        assertEquals(errorCode, exception.getErrorCode());
    }

    private CollateralLoanVerification pending() {
        return CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), application, LocalDateTime.of(2026, 8, 19, 8, 0)
        );
    }

    private CollateralLoanVerification completed(CollateralLoanManualVerificationOutcome outcome) {
        return pending().completeManualReview(
                outcome, UUID.randomUUID(), LocalDateTime.of(2026, 8, 19, 8, 30), "Assessment."
        );
    }
}

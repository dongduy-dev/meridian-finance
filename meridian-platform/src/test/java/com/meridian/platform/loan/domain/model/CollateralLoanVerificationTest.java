package com.meridian.platform.loan.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollateralLoanVerificationTest {

    @Test
    void createsOnlyPendingManualReviewForCollateralApplication() {
        CollateralLoanVerification verification = CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), collateralApplication(), LocalDateTime.parse("2026-08-13T09:00:00")
        );

        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW, verification.productVerificationResult());
        assertThrows(IllegalArgumentException.class, () -> new CollateralLoanVerification(
                UUID.randomUUID(), UUID.randomUUID(), ProductVerificationResult.VERIFIED, LocalDateTime.now()
        ));
    }

    @Test
    void rejectsNonCollateralApplication() {
        LoanApplication ucl = new LoanApplication(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UCL-20260813-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.DOCUMENTS_PENDING, new BigDecimal("5000000"), 6,
                LocalDateTime.parse("2026-08-13T09:00:00")
        );
        assertThrows(IllegalArgumentException.class, () -> CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), ucl, LocalDateTime.now()
        ));
    }

    private LoanApplication collateralApplication() {
        return new LoanApplication(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CL-20260813-000001",
                ProductCode.COLLATERAL_LOAN, ProductType.SECURED, LoanApplicationStatus.DOCUMENTS_PENDING,
                new BigDecimal("25000000"), 12, LocalDateTime.parse("2026-08-13T09:00:00")
        );
    }
}

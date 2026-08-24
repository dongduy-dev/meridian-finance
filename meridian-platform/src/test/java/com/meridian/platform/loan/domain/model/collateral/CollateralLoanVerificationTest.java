package com.meridian.platform.loan.domain.model.collateral;

import com.meridian.platform.loan.domain.model.*;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollateralLoanVerificationTest {

    @Test
    void createsOnlyPendingManualReviewForCollateralApplication() {
        CollateralLoanVerification verification = CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), collateralApplication(), LocalDateTime.parse("2026-08-13T09:00:00")
        );

        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW, verification.productVerificationResult());
        assertEquals(1, verification.verificationSequence());
        assertNull(verification.sourceCorrectionRequestId());
        assertThrows(IllegalArgumentException.class, () -> new CollateralLoanVerification(
                UUID.randomUUID(), UUID.randomUUID(), ProductVerificationResult.VERIFIED, LocalDateTime.now()
        ));
    }

    @Test
    void createsLaterPendingCycleLinkedToCorrection() {
        UUID correctionId = UUID.randomUUID();

        CollateralLoanVerification verification = CollateralLoanVerification.pendingReverification(
                UUID.randomUUID(), UUID.randomUUID(), 2, correctionId,
                LocalDateTime.parse("2026-08-13T10:00:00")
        );

        assertEquals(2, verification.verificationSequence());
        assertEquals(correctionId, verification.sourceCorrectionRequestId());
        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW, verification.productVerificationResult());
    }

    @Test
    void enforcesSequenceAndSourceInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new CollateralLoanVerification(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW, LocalDateTime.now(),
                null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new CollateralLoanVerification(
                UUID.randomUUID(), UUID.randomUUID(), 2, null,
                ProductVerificationResult.PENDING_MANUAL_REVIEW, LocalDateTime.now(),
                null, null, null
        ));
    }

    @Test
    void completesEverySupportedOutcomeWithNormalizedNote() {
        for (CollateralLoanManualVerificationOutcome outcome
                : CollateralLoanManualVerificationOutcome.values()) {
            CollateralLoanVerification completed = pending().completeManualReview(
                    outcome,
                    UUID.randomUUID(),
                    LocalDateTime.parse("2026-08-13T10:00:00"),
                    "  Assessment complete.  "
            );

            assertEquals(ProductVerificationResult.valueOf(outcome.name()),
                    completed.productVerificationResult());
            assertEquals("Assessment complete.", completed.assessmentNote());
        }
    }

    @Test
    void rejectsBlankOrOversizedAssessment() {
        BusinessRuleViolationException blank = assertThrows(
                BusinessRuleViolationException.class,
                () -> pending().completeManualReview(
                        CollateralLoanManualVerificationOutcome.VERIFIED,
                        UUID.randomUUID(), LocalDateTime.now(), "   "
                )
        );
        BusinessRuleViolationException longNote = assertThrows(
                BusinessRuleViolationException.class,
                () -> pending().completeManualReview(
                        CollateralLoanManualVerificationOutcome.VERIFIED,
                        UUID.randomUUID(), LocalDateTime.now(), "x".repeat(2001)
                )
        );

        assertEquals("COLLATERAL_VERIFICATION_ASSESSMENT_REQUIRED", blank.getErrorCode());
        assertEquals("COLLATERAL_VERIFICATION_ASSESSMENT_REQUIRED", longNote.getErrorCode());
    }

    @Test
    void rejectsPartialCompletionEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new CollateralLoanVerification(
                UUID.randomUUID(), UUID.randomUUID(), 1, null,
                ProductVerificationResult.VERIFIED, LocalDateTime.now().minusHours(1),
                UUID.randomUUID(), null, "Assessment."
        ));
    }

    @Test
    void rejectsSecondCompletion() {
        CollateralLoanVerification completed = pending().completeManualReview(
                CollateralLoanManualVerificationOutcome.VERIFIED,
                UUID.randomUUID(), LocalDateTime.now(), "Assessment."
        );

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> completed.completeManualReview(
                        CollateralLoanManualVerificationOutcome.FAILED,
                        UUID.randomUUID(), LocalDateTime.now(), "Second assessment."
                )
        );

        assertEquals("PRODUCT_VERIFICATION_NOT_PENDING", exception.getErrorCode());
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

    private CollateralLoanVerification pending() {
        return CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), collateralApplication(),
                LocalDateTime.parse("2026-08-13T09:00:00")
        );
    }
}

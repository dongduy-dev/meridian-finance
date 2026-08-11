package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnsecuredConsumerLoanVerificationTest {

    private static final UUID VERIFICATION_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 11, 8, 0);

    @Test
    void pendingVerificationKeepsDecisionEvidenceEmpty() {
        UnsecuredConsumerLoanVerification verification = pendingVerification();

        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW, verification.productVerificationResult());
        assertNull(verification.reviewedByUserId());
        assertNull(verification.reviewedAt());
        assertNull(verification.assessmentNote());
    }

    @Test
    void manualCompletionProducesVerifiedAuthoritativeEvidenceAndNormalizesNote() {
        LocalDateTime reviewedAt = CREATED_AT.plusHours(1);

        UnsecuredConsumerLoanVerification completed = pendingVerification()
                .completeManualReview(REVIEWER_ID, reviewedAt, "  Evidence is consistent.  ");

        assertEquals(ProductVerificationResult.VERIFIED, completed.productVerificationResult());
        assertEquals(REVIEWER_ID, completed.reviewedByUserId());
        assertEquals(reviewedAt, completed.reviewedAt());
        assertEquals("Evidence is consistent.", completed.assessmentNote());
    }

    @Test
    void manualCompletionRejectsBlankNote() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> pendingVerification().completeManualReview(REVIEWER_ID, CREATED_AT.plusHours(1), "   ")
        );

        assertEquals("UCL_VERIFICATION_ASSESSMENT_REQUIRED", exception.getErrorCode());
    }

    @Test
    void completedVerificationCannotBeCompletedAgain() {
        UnsecuredConsumerLoanVerification completed = pendingVerification()
                .completeManualReview(REVIEWER_ID, CREATED_AT.plusHours(1), "Evidence is consistent.");

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> completed.completeManualReview(REVIEWER_ID, CREATED_AT.plusHours(2), "Repeated decision.")
        );

        assertEquals("PRODUCT_VERIFICATION_NOT_PENDING", exception.getErrorCode());
    }

    @Test
    void pendingVerificationRejectsCompletedDecisionEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new UnsecuredConsumerLoanVerification(
                VERIFICATION_ID,
                APPLICATION_ID,
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                CREATED_AT,
                REVIEWER_ID,
                CREATED_AT.plusHours(1),
                "Unexpected evidence."
        ));
    }

    @Test
    void futureNegativeOutcomeMayRemainWithoutDecisionEvidenceButCannotBePartial() {
        UnsecuredConsumerLoanVerification failed = new UnsecuredConsumerLoanVerification(
                VERIFICATION_ID,
                APPLICATION_ID,
                ProductVerificationResult.FAILED,
                CREATED_AT,
                null,
                null,
                null
        );

        assertEquals(ProductVerificationResult.FAILED, failed.productVerificationResult());
        assertThrows(IllegalArgumentException.class, () -> new UnsecuredConsumerLoanVerification(
                VERIFICATION_ID,
                APPLICATION_ID,
                ProductVerificationResult.REQUIRES_MORE_INFORMATION,
                CREATED_AT,
                REVIEWER_ID,
                null,
                null
        ));
    }

    private UnsecuredConsumerLoanVerification pendingVerification() {
        return UnsecuredConsumerLoanVerification.pendingManualReview(
                VERIFICATION_ID,
                new LoanApplication(
                        APPLICATION_ID,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "UCL-20260811-000001",
                        ProductCode.UNSECURED_CONSUMER_LOAN,
                        ProductType.UNSECURED,
                        LoanApplicationStatus.SUBMITTED,
                        BigDecimal.valueOf(5_000_000).setScale(2),
                        6,
                        CREATED_AT
                ),
                CREATED_AT
        );
    }
}

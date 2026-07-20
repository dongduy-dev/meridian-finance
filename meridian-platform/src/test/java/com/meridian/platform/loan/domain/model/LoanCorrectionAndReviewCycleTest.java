package com.meridian.platform.loan.domain.model;

import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanCorrectionAndReviewCycleTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 7, 19, 8, 0);

    @Test
    void activeCycleCanCompleteSupersedeOrRequireCorrection() {
        LoanApplicationReviewCycle active = activeCycle();

        assertEquals(LoanReviewCycleStatus.COMPLETED, active.complete(STARTED_AT.plusMinutes(1)).status());
        assertEquals(LoanReviewCycleStatus.SUPERSEDED, active.supersede(STARTED_AT.plusMinutes(1)).status());
        assertEquals(LoanReviewCycleStatus.CORRECTION_REQUIRED,
                active.requireCorrection(STARTED_AT.plusMinutes(1)).status());
    }

    @Test
    void onlyCorrectionRequiredCycleCanBecomeCorrected() {
        LoanApplicationReviewCycle correctionRequired = activeCycle().requireCorrection(STARTED_AT.plusMinutes(1));

        assertEquals(LoanReviewCycleStatus.CORRECTED,
                correctionRequired.corrected(STARTED_AT.plusMinutes(2)).status());
        assertThrows(BusinessStateConflictException.class,
                () -> activeCycle().corrected(STARTED_AT.plusMinutes(2)));
    }

    @Test
    void taskCompletionIsIdempotentOnlyForSameActorAndRequest() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        LoanCorrectionTask task = openTask();
        LoanCorrectionTask completed = task.complete(actorId, requestId, STARTED_AT.plusMinutes(1));

        assertSame(completed, completed.complete(actorId, requestId, STARTED_AT.plusMinutes(2)));
        assertThrows(BusinessStateConflictException.class,
                () -> completed.complete(actorId, UUID.randomUUID(), STARTED_AT.plusMinutes(2)));
        assertThrows(BusinessStateConflictException.class,
                () -> completed.complete(UUID.randomUUID(), requestId, STARTED_AT.plusMinutes(2)));
    }

    @Test
    void requestBecomesReadyOnlyAfterEveryTaskCompletesAndResubmitsIdempotently() {
        LoanCorrectionTask open = openTask();
        LoanCorrectionRequest request = request();
        assertThrows(BusinessStateConflictException.class,
                () -> request.markReady(List.of(open), STARTED_AT.plusMinutes(1)));

        LoanCorrectionTask completed = open.complete(UUID.randomUUID(), UUID.randomUUID(), STARTED_AT.plusMinutes(1));
        LoanCorrectionRequest ready = request.markReady(List.of(completed), STARTED_AT.plusMinutes(2));
        UUID resubmissionId = UUID.randomUUID();
        LoanCorrectionRequest resubmitted = ready.resubmit(resubmissionId, STARTED_AT.plusMinutes(3));

        assertEquals(LoanCorrectionRequestStatus.RESUBMITTED, resubmitted.status());
        assertSame(resubmitted, resubmitted.resubmit(resubmissionId, STARTED_AT.plusMinutes(4)));
        assertThrows(BusinessStateConflictException.class,
                () -> resubmitted.resubmit(UUID.randomUUID(), STARTED_AT.plusMinutes(4)));
    }

    private LoanApplicationReviewCycle activeCycle() {
        return LoanApplicationReviewCycle.active(UUID.randomUUID(), UUID.randomUUID(), 1, STARTED_AT);
    }

    private LoanCorrectionTask openTask() {
        return new LoanCorrectionTask(
                UUID.randomUUID(), UUID.randomUUID(), 1,
                LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                DocumentType.RECENT_PAYSLIP,
                true, UUID.randomUUID(), null,
                "Upload evidence.", null,
                LoanCorrectionTaskStatus.OPEN,
                null, null, null, STARTED_AT
        );
    }

    private LoanCorrectionRequest request() {
        return new LoanCorrectionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "RETURN_TO_CUSTOMER_REVISION",
                com.meridian.platform.approval.domain.model.CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                UUID.randomUUID(), LoanCorrectionRequestStatus.OPEN,
                null, STARTED_AT, null, null
        );
    }
}

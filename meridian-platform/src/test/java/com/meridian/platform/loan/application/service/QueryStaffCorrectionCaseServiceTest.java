package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffCorrectionCaseServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 11, 0);

    @Mock LoanApplicationRepository applications;
    @Mock LoanCorrectionRepository corrections;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUserProvider;
    private QueryStaffCorrectionCaseService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffCorrectionCaseService(
                applications, corrections, documents, currentUserProvider);
        when(currentUserProvider.currentUser()).thenReturn(staff(CREATOR_ID));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
    }

    @Test
    void returnsSafeEmptyStateWhenNoCorrectionExists() {
        when(corrections.findLatestRequestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.empty());

        var result = service.query(APPLICATION_ID);

        assertNull(result.correctionRequest());
        assertEquals("MER-2026-000001", result.applicationNumber());
    }

    @Test
    void projectsMixedTasksProofAndCurrentActorMakerChecker() {
        LoanCorrectionRequest request = new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, UUID.randomUUID(), "REQUEST_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, CREATOR_ID,
                LoanCorrectionRequestStatus.OPEN, null, NOW.minusHours(3), null, null);
        LoanCorrectionTask customer = new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 1, LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, VERSION_ID, "Replace it.", null,
                LoanCorrectionTaskStatus.COMPLETED, UUID.randomUUID(), UUID.randomUUID(),
                NOW.minusHours(1), NOW.minusHours(2));
        LoanCorrectionTask staff = new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 2, LoanCorrectionResponsibility.STAFF,
                LoanCorrectionScope.DOCUMENT_REVIEW, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, VERSION_ID, null, "Review replacement.",
                LoanCorrectionTaskStatus.OPEN, null, null, null, NOW.minusHours(2));
        UUID replacementId = UUID.randomUUID();
        when(corrections.findLatestRequestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID))
                .thenReturn(List.of(customer, staff));
        when(documents.requireCurrentVersion(APPLICATION_ID, ITEM_ID)).thenReturn(replacementId);
        when(documents.isVersionReviewed(APPLICATION_ID, ITEM_ID, replacementId)).thenReturn(true);

        var result = service.query(APPLICATION_ID).correctionRequest();

        assertEquals(true, result.makerCheckerBlockedForCurrentActor());
        assertEquals(List.of("NOT_APPLICABLE", "SATISFIED"), result.tasks().stream()
                .map(task -> task.proofState()).toList());
        assertNull(result.tasks().getFirst().staffInstruction());
        assertEquals("Review replacement.", result.tasks().get(1).staffInstruction());
    }

    @Test
    void reportsStaffOnlyReadyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)),
                true
        );
    }

    @Test
    void reportsFullyCompletedMixedCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(
                        task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED),
                        task(2, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)
                ),
                true
        );
    }

    @Test
    void doesNotReportCustomerOnlyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED)),
                false
        );
    }

    @Test
    void doesNotReportCorrectionWithIncompleteStaffTaskAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.OPEN)),
                false
        );
    }

    @Test
    void doesNotReportNonReadyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.OPEN,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)),
                false
        );
    }

    private void assertStaffResubmissionReady(
            LoanCorrectionRequestStatus status,
            List<LoanCorrectionTask> tasks,
            boolean expected
    ) {
        when(corrections.findLatestRequestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(request(status)));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(tasks);

        var result = service.query(APPLICATION_ID).correctionRequest();

        assertEquals(expected, result.staffResubmissionReady());
    }

    private static LoanCorrectionRequest request(LoanCorrectionRequestStatus status) {
        return new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, UUID.randomUUID(), "REQUEST_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, UUID.randomUUID(), status,
                null, NOW.minusHours(3), null, null
        );
    }

    private static LoanCorrectionTask task(
            int sequence,
            LoanCorrectionResponsibility responsibility,
            LoanCorrectionTaskStatus status
    ) {
        LocalDateTime completedAt = status == LoanCorrectionTaskStatus.COMPLETED ? NOW.minusHours(1) : null;
        return new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, sequence, responsibility,
                responsibility == LoanCorrectionResponsibility.STAFF
                        ? LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD
                        : LoanCorrectionScope.DOCUMENT_REPLACEMENT,
                DocumentType.BANK_STATEMENT, false, ITEM_ID, VERSION_ID,
                responsibility == LoanCorrectionResponsibility.CUSTOMER ? "Replace it." : null,
                responsibility == LoanCorrectionResponsibility.STAFF ? "Upload it." : null,
                status,
                status == LoanCorrectionTaskStatus.COMPLETED ? UUID.randomUUID() : null,
                status == LoanCorrectionTaskStatus.COMPLETED ? UUID.randomUUID() : null,
                completedAt,
                NOW.minusHours(2)
        );
    }

    private static LoanApplication application() {
        return new LoanApplication(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), "MER-2026-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                BigDecimal.valueOf(10_000_000), 12, NOW.minusDays(2));
    }

    private static AuthenticatedUser staff(UUID userId) {
        return new AuthenticatedUser(
                userId, "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:correction:staff"));
    }
}

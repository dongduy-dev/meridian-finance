package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffCorrectionTaskServiceTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 17, 0);

    private LoanCorrectionRepository repository;
    private LoanDocumentChecklistPort documentPort;
    private BusinessAuditPublisher auditPublisher;
    private StaffCorrectionTaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoanCorrectionRepository.class);
        documentPort = mock(LoanDocumentChecklistPort.class);
        auditPublisher = mock(BusinessAuditPublisher.class);
        CurrentUserProvider userProvider = () -> user(STAFF_ID);
        service = new StaffCorrectionTaskService(
                repository,
                documentPort,
                userProvider,
                auditPublisher,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }

    @Test
    void completesStaffUploadTaskAndMarksRequestReady() {
        LoanCorrectionRequest request = request(CREATOR_ID);
        LoanCorrectionTask task = uploadTask();
        when(repository.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(repository.findRequestById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(repository.saveTask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findTasksByRequestIdForUpdate(REQUEST_ID)).thenAnswer(invocation ->
                List.of(task.complete(STAFF_ID, completionId(), NOW)));

        service.complete(TASK_ID, new CompleteCorrectionTaskRequest(completionId()));

        verify(documentPort).requireCurrentVersion(APPLICATION_ID, ITEM_ID);
        verify(repository).saveRequest(any(LoanCorrectionRequest.class));
        verify(auditPublisher).publish(any());
    }

    @Test
    void creatorCannotCompleteOwnStaffTask() {
        StaffCorrectionTaskService creatorService = new StaffCorrectionTaskService(
                repository,
                documentPort,
                () -> user(CREATOR_ID),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-07-19T17:00:00Z"), ZoneOffset.UTC)
        );
        when(repository.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(uploadTask()));
        when(repository.findRequestById(REQUEST_ID)).thenReturn(Optional.of(request(CREATOR_ID)));

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> creatorService.complete(
                        TASK_ID, new CompleteCorrectionTaskRequest(completionId()))
        );

        assertEquals("STAFF_CORRECTION_MAKER_CHECKER_VIOLATION", exception.getErrorCode());
        verify(repository, never()).saveTask(any());
    }

    @Test
    void documentReviewTaskRequiresPersistedReviewOfBaselineVersion() {
        LoanCorrectionTask task = new LoanCorrectionTask(
                TASK_ID, REQUEST_ID, 1, LoanCorrectionResponsibility.STAFF,
                LoanCorrectionScope.DOCUMENT_REVIEW, DocumentType.RECENT_PAYSLIP,
                false, ITEM_ID, VERSION_ID, null, "Review the payslip.",
                LoanCorrectionTaskStatus.OPEN, null, null, null, NOW.minusHours(1)
        );
        when(repository.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(repository.findRequestById(REQUEST_ID)).thenReturn(Optional.of(request(CREATOR_ID)));
        when(documentPort.isVersionReviewed(APPLICATION_ID, ITEM_ID, VERSION_ID))
                .thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.complete(
                        TASK_ID, new CompleteCorrectionTaskRequest(completionId()))
        );

        assertEquals("CORRECTION_TASK_PROOF_MISSING", exception.getErrorCode());
        verify(repository, never()).saveTask(any());
    }

    private LoanCorrectionRequest request(UUID creatorId) {
        return new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, UUID.randomUUID(),
                "REQUEST_STAFF_CORRECTION", CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED,
                creatorId, LoanCorrectionRequestStatus.OPEN, null,
                NOW.minusHours(2), null, null
        );
    }

    private LoanCorrectionTask uploadTask() {
        return new LoanCorrectionTask(
                TASK_ID, REQUEST_ID, 1, LoanCorrectionResponsibility.STAFF,
                LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, DocumentType.RECENT_PAYSLIP,
                true, ITEM_ID, null, null, "Upload the requested payslip.",
                LoanCorrectionTaskStatus.OPEN, null, null, null, NOW.minusHours(1)
        );
    }

    private UUID completionId() {
        return UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    }

    private AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(
                id, "staff@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:correction:staff")
        );
    }
}

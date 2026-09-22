package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
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
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistedCustomerCorrectionTaskServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID BASELINE_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID COMPLETION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);

    @Mock LoanApplicationRepository applications;
    @Mock LoanCorrectionRepository corrections;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUsers;
    @Mock BusinessAuditPublisher audits;
    private AssistedCustomerCorrectionTaskService service;

    @BeforeEach
    void setUp() {
        service = new AssistedCustomerCorrectionTaskService(
                applications, corrections, new CustomerCorrectionDocumentProof(documents),
                currentUsers, audits, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(currentUsers.currentUser()).thenReturn(staff(Set.of("loan:correction:staff")));
    }

    @Test
    void completesCustomerOwnedAssistedTaskWithStaffActorAndMarksRequestReady() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        LoanCorrectionRequest request = request(STAFF_ID);
        LoanCorrectionTask task = customerTask(LoanCorrectionTaskStatus.OPEN, null, null);
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(request));
        when(corrections.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(documents.hasCurrentVersionDifferentFrom(ITEM_ID, BASELINE_ID)).thenReturn(true);
        when(corrections.saveTask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.findTasksByRequestIdForUpdate(REQUEST_ID)).thenAnswer(invocation ->
                List.of(task.complete(STAFF_ID, COMPLETION_ID, NOW)));

        var result = service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID));

        assertEquals("COMPLETED", result.status());
        assertEquals("Replace the Customer bank statement.", result.customerInstruction());
        verify(applications).acquireWorkflowLock(APPLICATION_ID);
        verify(corrections).saveRequest(any(LoanCorrectionRequest.class));
        verify(audits).publish(any());
    }

    @Test
    void exactCompletionReplayReturnsExistingTaskWithoutAnotherEffect() {
        LoanCorrectionTask completed = customerTask(
                LoanCorrectionTaskStatus.COMPLETED, STAFF_ID, COMPLETION_ID);
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(request(UUID.randomUUID())));
        when(corrections.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(completed));

        var result = service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID));

        assertEquals("COMPLETED", result.status());
        verify(documents, never()).hasCurrentVersionDifferentFrom(any(), any());
        verify(corrections, never()).saveTask(any());
        verify(audits, never()).publish(any());
    }

    @Test
    void rejectsCustomerDigitalAndStaffOwnedTasks() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.CUSTOMER_DIGITAL)));
        AuthorizationException digital = assertThrows(AuthorizationException.class, () -> service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID)));
        assertEquals("STAFF_CORRECTION_ACCESS_DENIED", digital.getErrorCode());

        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(request(UUID.randomUUID())));
        LoanCorrectionTask staffTask = new LoanCorrectionTask(
                TASK_ID, REQUEST_ID, 1, LoanCorrectionResponsibility.STAFF,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, BASELINE_ID, null, "Review it.",
                LoanCorrectionTaskStatus.OPEN, null, null, null, NOW.minusHours(1));
        when(corrections.findTaskByIdForUpdate(TASK_ID)).thenReturn(Optional.of(staffTask));
        AuthorizationException wrongOwner = assertThrows(AuthorizationException.class, () -> service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID)));
        assertEquals("STAFF_CORRECTION_ACCESS_DENIED", wrongOwner.getErrorCode());
    }

    @Test
    void rejectsActorWithoutExactStaffCorrectionPermission() {
        when(currentUsers.currentUser()).thenReturn(staff(Set.of("loan:correction:staff.extra")));

        AuthorizationException error = assertThrows(AuthorizationException.class, () -> service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID)));

        assertEquals("STAFF_CORRECTION_ACCESS_DENIED", error.getErrorCode());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void rejectsCustomerPrincipalEvenIfItCarriesTheStaffPermission() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                STAFF_ID, "customer@meridian.test", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("loan:correction:staff")));

        AuthorizationException error = assertThrows(AuthorizationException.class, () -> service.complete(
                APPLICATION_ID, TASK_ID, new CompleteCorrectionTaskRequest(COMPLETION_ID)));

        assertEquals("STAFF_CORRECTION_ACCESS_DENIED", error.getErrorCode());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    private LoanApplication application(OriginationChannel channel) {
        return new LoanApplication(
                APPLICATION_ID, CUSTOMER_ID, UUID.randomUUID(), "UCL-ASSISTED-CP3",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED, channel,
                LoanApplicationStatus.RETURNED_FOR_REVISION, new BigDecimal("5000000"), 6,
                NOW.minusDays(1));
    }

    private LoanCorrectionRequest request(UUID creatorId) {
        return new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, null, "COMPLETE_PRODUCT_VERIFICATION",
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED, creatorId,
                LoanCorrectionRequestStatus.OPEN, null, NOW.minusHours(2), null, null);
    }

    private LoanCorrectionTask customerTask(
            LoanCorrectionTaskStatus status,
            UUID completedBy,
            UUID completionRequestId
    ) {
        return new LoanCorrectionTask(
                TASK_ID, REQUEST_ID, 1, LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, BASELINE_ID, "Replace the Customer bank statement.", null,
                status, completedBy, completionRequestId,
                status == LoanCorrectionTaskStatus.COMPLETED ? NOW.minusMinutes(5) : null,
                NOW.minusHours(1));
    }

    private AuthenticatedUser staff(Set<String> permissions) {
        return new AuthenticatedUser(
                STAFF_ID, "loan.officer@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), permissions);
    }
}

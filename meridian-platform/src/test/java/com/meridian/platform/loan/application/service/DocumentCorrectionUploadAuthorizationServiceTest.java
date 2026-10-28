package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.AuthorizeDocumentCorrectionUploadUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCorrectionUploadAuthorizationServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID BASELINE_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock LoanCorrectionRepository corrections;
    @Mock LoanApplicationRepository applications;
    private DocumentCorrectionUploadAuthorizationService adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentCorrectionUploadAuthorizationService(corrections, applications);
    }

    @Test
    void distinguishesStaffOwnedAndAssistedCustomerOwnedUploadAuthority() {
        LoanCorrectionTask staffTask = task(LoanCorrectionResponsibility.STAFF);
        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.of(staffTask));

        assertEquals(AuthorizeDocumentCorrectionUploadUseCase.StaffUploadAuthority.STAFF_CORRECTION,
                adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        verify(applications, never()).findById(APPLICATION_ID);

        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.empty());
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(corrections.findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.of(task(LoanCorrectionResponsibility.CUSTOMER)));

        assertEquals(AuthorizeDocumentCorrectionUploadUseCase.StaffUploadAuthority.ASSISTED_CUSTOMER_CORRECTION,
                adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
    }

    @Test
    void rejectsCustomerTaskForDigitalApplicationAndStaleBaseline() {
        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.empty());
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.CUSTOMER_DIGITAL)));

        AuthorizationException denied = assertThrows(AuthorizationException.class,
                () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        assertEquals("DOCUMENT_UPLOAD_DENIED", denied.getErrorCode());
        verify(corrections, never()).findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID);

        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.of(task(LoanCorrectionResponsibility.STAFF)));
        BusinessStateConflictException stale = assertThrows(BusinessStateConflictException.class,
                () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, UUID.randomUUID()));
        assertEquals("STALE_DOCUMENT_VERSION", stale.getErrorCode());
    }

    @Test
    void customerTaskLookupUsesExactItemAndRetainsNullAndStaleBaselineSemantics() {
        when(corrections.findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID)).thenReturn(Optional.of(task(LoanCorrectionResponsibility.CUSTOMER)));
        adapter.authorizeCustomerUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID);
        var stale = assertThrows(BusinessStateConflictException.class, () -> adapter.authorizeCustomerUpload(APPLICATION_ID, ITEM_ID, null));
        assertEquals("STALE_DOCUMENT_VERSION", stale.getErrorCode());
        assertEquals("The correction task document baseline no longer matches.", stale.getMessage());
        var wrong = assertThrows(AuthorizationException.class, () -> adapter.authorizeCustomerUpload(APPLICATION_ID, UUID.randomUUID(), BASELINE_ID));
        assertEquals("No open customer correction task authorizes this document upload.", wrong.getMessage());
        var task = task(LoanCorrectionResponsibility.CUSTOMER);
        var withoutBaseline = new LoanCorrectionTask(task.id(), task.correctionRequestId(), task.sequence(), task.responsibleParty(), task.scope(),
                task.documentType(), task.createChecklistItem(), task.checklistItemId(), null, task.customerInstruction(), task.staffInstruction(),
                task.status(), task.completedByUserId(), task.completionRequestId(), task.completedAt(), task.createdAt());
        when(corrections.findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID)).thenReturn(Optional.of(withoutBaseline));
        adapter.authorizeCustomerUpload(APPLICATION_ID, ITEM_ID, null);
        assertThrows(BusinessStateConflictException.class, () -> adapter.authorizeCustomerUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        verify(applications, never()).acquireWorkflowLock(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingApplicationAndMissingAssistedTaskRetainErrorPrecedence() {
        var missing = assertThrows(AuthorizationException.class, () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        assertEquals("No correction task authorizes this document upload.", missing.getMessage());
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        var missingTask = assertThrows(AuthorizationException.class, () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        assertEquals("No open assisted Customer correction task authorizes this document upload.", missingTask.getMessage());
        verify(applications, never()).acquireWorkflowLock(org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(LoanApplicationStatus.class)
    void assistedCustomerAuthorityRequiresItsExactStatusChannelAndSupportedProduct(LoanApplicationStatus status) {
        for (var channel : OriginationChannel.values()) for (var product : ProductCode.values()) {
            ProductType type = switch (product) {
                case SALARY_ADVANCE -> ProductType.SALARY_BASED;
                case UNSECURED_CONSUMER_LOAN -> ProductType.UNSECURED;
                case COLLATERAL_LOAN -> ProductType.SECURED;
            };
            if (product == ProductCode.SALARY_ADVANCE && channel == OriginationChannel.STAFF_ASSISTED) {
                assertEquals("Salary Advance permits only Customer-digital origination.",
                        assertThrows(IllegalArgumentException.class, () -> new LoanApplication(APPLICATION_ID,
                                UUID.randomUUID(), UUID.randomUUID(), "CORRECTION-SPI", product, type, channel,
                                status, new BigDecimal("5000000"), 6, LocalDateTime.of(2026, 10, 27, 12, 0))).getMessage());
                continue;
            }
            var application = new LoanApplication(APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), "CORRECTION-SPI",
                    product, type, channel, status, new BigDecimal("5000000"), 6,
                    LocalDateTime.of(2026, 10, 27, 12, 0));
            when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
            boolean permitted = status == LoanApplicationStatus.RETURNED_FOR_REVISION
                    && channel == OriginationChannel.STAFF_ASSISTED && product != ProductCode.SALARY_ADVANCE;
            if (permitted) {
                when(corrections.findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID))
                        .thenReturn(Optional.of(task(LoanCorrectionResponsibility.CUSTOMER)));
                assertEquals(AuthorizeDocumentCorrectionUploadUseCase.StaffUploadAuthority.ASSISTED_CUSTOMER_CORRECTION,
                        adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
            } else {
                var denied = assertThrows(AuthorizationException.class,
                        () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
                assertEquals("DOCUMENT_UPLOAD_DENIED", denied.getErrorCode());
                assertEquals("No correction task authorizes this document upload.", denied.getMessage());
            }
        }
        verify(applications, never()).acquireWorkflowLock(org.mockito.ArgumentMatchers.any());
        verify(applications, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repositoryFailuresPropagateWithoutFallbackAuthorization() {
        var failure = new IllegalStateException("correction lookup failure");
        org.mockito.Mockito.doThrow(failure).when(corrections).findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID);
        org.junit.jupiter.api.Assertions.assertSame(failure, assertThrows(IllegalStateException.class,
                () -> adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID)));
        verify(applications, never()).findById(APPLICATION_ID);
    }

    private static LoanCorrectionTask task(LoanCorrectionResponsibility responsibility) {
        return new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 1, responsibility,
                responsibility == LoanCorrectionResponsibility.CUSTOMER
                        ? LoanCorrectionScope.DOCUMENT_REPLACEMENT
                        : LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                com.meridian.platform.loan.domain.model.LoanDocumentType.BANK_STATEMENT, false, ITEM_ID, BASELINE_ID,
                responsibility == LoanCorrectionResponsibility.CUSTOMER ? "Replace it." : null,
                responsibility == LoanCorrectionResponsibility.STAFF ? "Upload it." : null,
                LoanCorrectionTaskStatus.OPEN, null, null, null,
                LocalDateTime.of(2026, 9, 22, 9, 0));
    }

    private static LoanApplication application(OriginationChannel channel) {
        return new LoanApplication(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), "UCL-CP3",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED, channel,
                LoanApplicationStatus.RETURNED_FOR_REVISION, new BigDecimal("5000000"), 6,
                LocalDateTime.of(2026, 9, 21, 9, 0));
    }
}

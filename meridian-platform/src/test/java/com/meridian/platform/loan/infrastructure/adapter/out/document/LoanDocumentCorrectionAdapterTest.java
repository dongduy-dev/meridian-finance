package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.LoanDocumentCorrectionPort;
import com.meridian.platform.document.domain.model.DocumentType;
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
class LoanDocumentCorrectionAdapterTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID BASELINE_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock LoanCorrectionRepository corrections;
    @Mock LoanApplicationRepository applications;
    private LoanDocumentCorrectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LoanDocumentCorrectionAdapter(corrections, applications);
    }

    @Test
    void distinguishesStaffOwnedAndAssistedCustomerOwnedUploadAuthority() {
        LoanCorrectionTask staffTask = task(LoanCorrectionResponsibility.STAFF);
        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.of(staffTask));

        assertEquals(LoanDocumentCorrectionPort.StaffUploadAuthority.STAFF_CORRECTION,
                adapter.authorizeStaffUpload(APPLICATION_ID, ITEM_ID, BASELINE_ID));
        verify(applications, never()).findById(APPLICATION_ID);

        when(corrections.findOpenStaffDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.empty());
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(corrections.findOpenCustomerDocumentTask(APPLICATION_ID, ITEM_ID))
                .thenReturn(Optional.of(task(LoanCorrectionResponsibility.CUSTOMER)));

        assertEquals(LoanDocumentCorrectionPort.StaffUploadAuthority.ASSISTED_CUSTOMER_CORRECTION,
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

    private static LoanCorrectionTask task(LoanCorrectionResponsibility responsibility) {
        return new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 1, responsibility,
                responsibility == LoanCorrectionResponsibility.CUSTOMER
                        ? LoanCorrectionScope.DOCUMENT_REPLACEMENT
                        : LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                DocumentType.BANK_STATEMENT, false, ITEM_ID, BASELINE_ID,
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

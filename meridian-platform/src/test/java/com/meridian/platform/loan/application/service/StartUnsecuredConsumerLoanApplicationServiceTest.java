package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartUnsecuredConsumerLoanApplicationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final LoanProduct PRODUCT = new LoanProduct(
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            ProductCode.UNSECURED_CONSUMER_LOAN,
            ProductType.UNSECURED,
            "Unsecured Consumer Loan",
            null,
            true,
            new BigDecimal("2000000"),
            new BigDecimal("50000000")
    );

    @Mock private LoanProductRepository products;
    @Mock private LoanApplicationRepository applications;
    @Mock private LoanDocumentChecklistPort checklists;
    @Mock private UnsecuredConsumerLoanVerificationRepository verifications;
    @Mock private CustomerReadinessPort readiness;
    @Mock private OutstandingLoanAccountQuery outstandingLoanAccounts;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock private BusinessAuditPublisher auditPublisher;

    private StartUnsecuredConsumerLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StartUnsecuredConsumerLoanApplicationService(
                products,
                applications,
                checklists,
                verifications,
                readiness,
                outstandingLoanAccounts,
                new LoanMapper(),
                currentUserProvider,
                transitionRecorder,
                auditPublisher,
                Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC)
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                USER_ID, "customer@meridian.local", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("loan:submit")
        ));
        lenient().when(outstandingLoanAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.CLEAR);
    }

    @Test
    void createsDocumentsPendingUclWithPendingManualReviewAndAuditHistory() {
        arrangeReadyCustomerAndProduct();
        when(applications.nextApplicationNumberSequence()).thenReturn(42L);
        when(checklists.resolveSubmissionInitialState(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistInitialState(false));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UnsecuredConsumerLoanApplicationDto result = service.startUnsecuredConsumerLoanApplication(
                new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("5000000"), 6)
        );

        assertNotNull(result.loanApplicationId());
        assertEquals("UCL-20260811-000042", result.applicationNumber());
        assertEquals("UNSECURED_CONSUMER_LOAN", result.productCode());
        assertEquals("UNSECURED", result.productType());
        assertEquals("DOCUMENTS_PENDING", result.status());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());

        ArgumentCaptor<LoanApplication> applicationCaptor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applications).save(applicationCaptor.capture());
        assertEquals(CUSTOMER_ID, applicationCaptor.getValue().customerId());
        assertEquals(LoanApplicationStatus.DOCUMENTS_PENDING, applicationCaptor.getValue().status());
        verify(applications).acquireCustomerProductLock(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN);
        verify(checklists).createSubmissionChecklist(any(),
                org.mockito.ArgumentMatchers.eq(ProductCode.UNSECURED_CONSUMER_LOAN), any());

        ArgumentCaptor<UnsecuredConsumerLoanVerification> verificationCaptor =
                ArgumentCaptor.forClass(UnsecuredConsumerLoanVerification.class);
        verify(verifications).save(verificationCaptor.capture());
        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW,
                verificationCaptor.getValue().productVerificationResult());
        verify(transitionRecorder).record(any(), any(), org.mockito.ArgumentMatchers.isNull());

        ArgumentCaptor<BusinessAuditEvent> auditCaptor = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertEquals(BusinessAuditAction.UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED,
                auditCaptor.getValue().entries().getFirst().action());
    }

    @Test
    void rejectsMissingInactiveOrIncompleteCustomerBeforeLoanEffects() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertEquals("CUSTOMER_NOT_FOUND", assertThrows(
                EntityNotFoundException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(false, true, true)));
        assertEquals("CUSTOMER_NOT_ACTIVE", assertThrows(
                BusinessStateConflictException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, false, true)));
        assertEquals("PROFILE_INCOMPLETE", assertThrows(
                BusinessRuleViolationException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, false)));
        assertEquals("PRIMARY_BANK_ACCOUNT_REQUIRED", assertThrows(
                BusinessRuleViolationException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());
        verifyNoInteractions(products, applications, checklists, verifications);
    }

    @Test
    void rejectsMissingInactiveAndBlockingProductStates() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, true)));
        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)).thenReturn(Optional.empty());
        assertEquals("PRODUCT_NOT_FOUND", assertThrows(
                EntityNotFoundException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());

        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(new LoanProduct(
                        PRODUCT.id(), PRODUCT.productCode(), PRODUCT.productType(), PRODUCT.name(),
                        PRODUCT.description(), false, PRODUCT.minAmount(), PRODUCT.maxAmount()
                )));
        assertEquals("PRODUCT_INACTIVE", assertThrows(
                BusinessRuleViolationException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());

        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)).thenReturn(Optional.of(PRODUCT));
        when(applications.existsByCustomerIdAndProductCodeAndStatusIn(
                CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN, LoanApplicationStatus.blockingStatuses()
        )).thenReturn(true);
        assertEquals("BLOCKING_APPLICATION_EXISTS", assertThrows(
                BusinessStateConflictException.class, () -> service.startUnsecuredConsumerLoanApplication(request())
        ).getErrorCode());
        verify(checklists, never()).createSubmissionChecklist(any(), any(), any());
        verifyNoInteractions(verifications);
    }

    @Test
    void blocksOutstandingUclAndFailsClosedForInconsistentAccountEvidence() {
        arrangeReadyCustomerAndProduct();
        when(outstandingLoanAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS);

        BusinessStateConflictException outstanding = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startUnsecuredConsumerLoanApplication(request())
        );
        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", outstanding.getErrorCode());

        when(outstandingLoanAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.INCONSISTENT);
        BusinessStateConflictException inconsistent = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startUnsecuredConsumerLoanApplication(request())
        );
        assertEquals("SYSTEM_STATE_CONFLICT", inconsistent.getErrorCode());
        verify(checklists, never()).createSubmissionChecklist(any(), any(), any());
        verifyNoInteractions(verifications);
    }

    private void arrangeReadyCustomerAndProduct() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, true)));
        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)).thenReturn(Optional.of(PRODUCT));
    }

    private CustomerReadinessSnapshot snapshot(boolean active, boolean profileComplete, boolean hasBankAccount) {
        return new CustomerReadinessSnapshot(CUSTOMER_ID, active, profileComplete, hasBankAccount, "UNVERIFIED");
    }

    private UnsecuredConsumerLoanApplicationRequest request() {
        return new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("5000000"), 6);
    }
}

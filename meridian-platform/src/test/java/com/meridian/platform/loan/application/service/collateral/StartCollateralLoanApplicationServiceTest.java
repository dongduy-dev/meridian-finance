package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;

import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartCollateralLoanApplicationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CHECKLIST_ITEM_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final LoanProduct PRODUCT = new LoanProduct(
            UUID.fromString("10000000-0000-0000-0000-000000000003"),
            ProductCode.COLLATERAL_LOAN,
            ProductType.SECURED,
            "Collateral Loan",
            null,
            true,
            new BigDecimal("5000000"),
            new BigDecimal("100000000")
    );

    @Mock private LoanProductRepository products;
    @Mock private LoanApplicationRepository applications;
    @Mock private CollateralRepository collaterals;
    @Mock private LoanDocumentChecklistPort checklists;
    @Mock private CollateralLoanVerificationRepository verifications;
    @Mock private CustomerReadinessPort readiness;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock private BusinessAuditPublisher auditPublisher;

    private StartCollateralLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StartCollateralLoanApplicationService(
                products,
                applications,
                collaterals,
                checklists,
                verifications,
                readiness,
                new LoanMapper(),
                currentUserProvider,
                transitionRecorder,
                auditPublisher,
                Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneOffset.UTC)
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                USER_ID, "customer@meridian.local", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("loan:submit")
        ));
    }

    @Test
    void createsDocumentsPendingApplicationWithStructuredFactsEvidenceAndPendingVerification() {
        arrangeReadyCustomerAndProduct();
        when(applications.nextApplicationNumberSequence()).thenReturn(42L);
        when(checklists.resolveSubmissionInitialState(ProductCode.COLLATERAL_LOAN))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistInitialState(false));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collaterals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checklists.createSubmissionChecklist(any(), any(), any())).thenReturn(checklist());

        CollateralLoanApplicationDto result = service.startCollateralLoanApplication(request(
                new BigDecimal("100000000"), new BigDecimal("5000000")
        ));

        assertNotNull(result.loanApplicationId());
        assertEquals("CL-20260813-000042", result.applicationNumber());
        assertEquals("COLLATERAL_LOAN", result.productCode());
        assertEquals("SECURED", result.productType());
        assertEquals("DOCUMENTS_PENDING", result.status());
        assertEquals("MOTORBIKE", result.collateralType());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());
        assertEquals(CHECKLIST_ITEM_ID, result.evidenceRequirements().getFirst().checklistItemId());
        assertEquals("COLLATERAL_OWNERSHIP_EVIDENCE",
                result.evidenceRequirements().getFirst().documentType());
        assertEquals("REQUIRED", result.evidenceRequirements().getFirst().requirementStatus());

        ArgumentCaptor<LoanApplication> applicationCaptor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applications).save(applicationCaptor.capture());
        assertEquals(CUSTOMER_ID, applicationCaptor.getValue().customerId());
        assertEquals(LoanApplicationStatus.DOCUMENTS_PENDING, applicationCaptor.getValue().status());
        verify(applications).acquireCustomerProductLock(CUSTOMER_ID, ProductCode.COLLATERAL_LOAN);

        ArgumentCaptor<Collateral> collateralCaptor = ArgumentCaptor.forClass(Collateral.class);
        verify(collaterals).save(collateralCaptor.capture());
        assertEquals(result.loanApplicationId(), collateralCaptor.getValue().loanApplicationId());
        assertEquals("2024 Honda motorbike", collateralCaptor.getValue().description());
        assertEquals(new BigDecimal("5000000"), collateralCaptor.getValue().estimatedValue());

        ArgumentCaptor<CollateralLoanVerification> verificationCaptor =
                ArgumentCaptor.forClass(CollateralLoanVerification.class);
        verify(verifications).save(verificationCaptor.capture());
        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW,
                verificationCaptor.getValue().productVerificationResult());
        verify(transitionRecorder).record(any(), any(), org.mockito.ArgumentMatchers.isNull());

        ArgumentCaptor<BusinessAuditEvent> auditCaptor = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertEquals(BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED,
                auditCaptor.getValue().entries().getFirst().action());
        assertTrue(auditCaptor.getValue().entries().getFirst().payload().values().isEmpty());
    }

    @Test
    void rejectsCustomerReadinessFailuresBeforeLoanEffects() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertEquals("CUSTOMER_NOT_FOUND", assertThrows(
                EntityNotFoundException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(false, true, true)));
        assertEquals("CUSTOMER_NOT_ACTIVE", assertThrows(
                BusinessStateConflictException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, false, true)));
        assertEquals("PROFILE_INCOMPLETE", assertThrows(
                BusinessRuleViolationException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());

        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, false)));
        assertEquals("PRIMARY_BANK_ACCOUNT_REQUIRED", assertThrows(
                BusinessRuleViolationException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());
        verifyNoInteractions(products, applications, collaterals, checklists, verifications);
    }

    @Test
    void rejectsInvalidCollateralDetailsBeforeLockOrPersistenceEffects() {
        arrangeReadyCustomerAndProduct();

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startCollateralLoanApplication(request(
                        new BigDecimal("25000000"),
                        new BigDecimal("35000000.50")
                ))
        );

        assertEquals("INVALID_COLLATERAL_DETAILS", exception.getErrorCode());
        verify(applications, never()).acquireCustomerProductLock(CUSTOMER_ID, ProductCode.COLLATERAL_LOAN);
        verify(applications, never()).existsByCustomerIdAndProductCodeAndStatusIn(
                CUSTOMER_ID,
                ProductCode.COLLATERAL_LOAN,
                LoanApplicationStatus.blockingStatuses()
        );
        verify(applications, never()).nextApplicationNumberSequence();
        verify(applications, never()).save(any());
        verifyNoInteractions(collaterals, checklists, verifications, transitionRecorder, auditPublisher);
    }

    @Test
    void rejectsMissingInactiveAndBlockingProductStatesBeforePersistence() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, true)));
        when(products.findByProductCode(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.empty());
        assertEquals("PRODUCT_NOT_FOUND", assertThrows(
                EntityNotFoundException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());

        when(products.findByProductCode(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.of(new LoanProduct(
                PRODUCT.id(), PRODUCT.productCode(), PRODUCT.productType(), PRODUCT.name(), null,
                false, PRODUCT.minAmount(), PRODUCT.maxAmount()
        )));
        assertEquals("PRODUCT_INACTIVE", assertThrows(
                BusinessRuleViolationException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());

        when(products.findByProductCode(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.of(PRODUCT));
        when(applications.existsByCustomerIdAndProductCodeAndStatusIn(
                CUSTOMER_ID, ProductCode.COLLATERAL_LOAN, LoanApplicationStatus.blockingStatuses()
        )).thenReturn(true);
        assertEquals("BLOCKING_APPLICATION_EXISTS", assertThrows(
                BusinessStateConflictException.class, () -> service.startCollateralLoanApplication(request())
        ).getErrorCode());
        verify(applications, never()).save(any());
        verifyNoInteractions(collaterals, checklists, verifications);
    }

    private void arrangeReadyCustomerAndProduct() {
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(snapshot(true, true, true)));
        when(products.findByProductCode(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.of(PRODUCT));
    }

    private CustomerReadinessSnapshot snapshot(boolean active, boolean profileComplete, boolean hasBankAccount) {
        return new CustomerReadinessSnapshot(CUSTOMER_ID, active, profileComplete, hasBankAccount, "UNVERIFIED");
    }

    private LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist() {
        return new LoanDocumentChecklistPort.SubmissionChecklistSnapshot(List.of(
                new LoanDocumentChecklistPort.SubmissionChecklistItemSnapshot(
                        CHECKLIST_ITEM_ID,
                        DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                        DocumentRequirementStatus.REQUIRED
                )
        ));
    }

    private CollateralLoanApplicationRequest request() {
        return request(new BigDecimal("25000000"), new BigDecimal("35000000"));
    }

    private CollateralLoanApplicationRequest request(BigDecimal amount, BigDecimal estimatedValue) {
        return new CollateralLoanApplicationRequest(
                amount,
                12,
                new CollateralDetailsRequest(
                        CollateralType.MOTORBIKE,
                        "  2024 Honda motorbike  ",
                        estimatedValue,
                        "  Customer-provided ownership statement  ",
                        "  Normal used condition  "
                )
        );
    }
}

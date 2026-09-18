package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartAssistedCollateralLoanServiceTest {

    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STAFF_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 8, 0);
    private static final LoanProduct PRODUCT = new LoanProduct(
            UUID.fromString("10000000-0000-0000-0000-000000000003"),
            ProductCode.COLLATERAL_LOAN, ProductType.SECURED, "Collateral Loan", null, true,
            new BigDecimal("5000000"), new BigDecimal("100000000"));

    @Mock AssistedOriginationCaseRepository cases;
    @Mock LoanIntakeEvidencePort intakeEvidence;
    @Mock LoanProductRepository products;
    @Mock LoanApplicationRepository applications;
    @Mock CollateralRepository collaterals;
    @Mock LoanDocumentChecklistPort checklists;
    @Mock CollateralLoanVerificationRepository verifications;
    @Mock CustomerReadinessPort customers;
    @Mock LoanApplicationStatusTransitionRecorder transitions;
    @Mock CurrentUserProvider currentUsers;
    @Mock BusinessAuditPublisher audits;

    private StartAssistedCollateralLoanService service;

    @BeforeEach
    void setUp() {
        service = new StartAssistedCollateralLoanService(
                cases, intakeEvidence, products, applications, collaterals, checklists,
                verifications, customers, transitions, currentUsers, audits,
                Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC));
        when(currentUsers.currentUser()).thenReturn(staff(null, Set.of("loan:originate:staff")));
    }

    @Test
    void rejectsNonStaffMissingPermissionAndStaffCustomerContext() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                STAFF_ID, "customer@meridian.local", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("loan:originate:staff")));
        assertThrows(AuthorizationException.class, () -> service.submit(CASE_ID, request()));

        when(currentUsers.currentUser()).thenReturn(staff(null, Set.of()));
        assertThrows(AuthorizationException.class, () -> service.submit(CASE_ID, request()));

        when(currentUsers.currentUser()).thenReturn(staff(CUSTOMER_ID, Set.of("loan:originate:staff")));
        assertThrows(AuthorizationException.class, () -> service.submit(CASE_ID, request()));
    }

    @Test
    void rejectsMissingTerminalWrongProductAndMissingCustomerCasesBeforeEvidence() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.empty());
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_FOUND", assertThrows(
                EntityNotFoundException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.COLLATERAL_LOAN, CUSTOMER_ID, AssistedOriginationCaseStatus.ABANDONED, NOW, null)));
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.COLLATERAL_LOAN, CUSTOMER_ID, AssistedOriginationCaseStatus.COMPLETED, NOW,
                UUID.randomUUID())));
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.UNSECURED_CONSUMER_LOAN, CUSTOMER_ID, AssistedOriginationCaseStatus.OPEN, null, null)));
        assertEquals("ASSISTED_ORIGINATION_PRODUCT_MISMATCH", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.COLLATERAL_LOAN, null, AssistedOriginationCaseStatus.OPEN, null, null)));
        assertEquals("ASSISTED_ORIGINATION_CUSTOMER_REQUIRED", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());
        verify(intakeEvidence, never()).requireCurrentCollateralPaperApplication(any());
    }

    @Test
    void requiresCurrentCollateralPaperApplicationBeforeLoanEffects() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(openCase()));
        doThrow(new BusinessRuleViolationException("COLLATERAL_PAPER_APPLICATION_REQUIRED", "Required"))
                .when(intakeEvidence).requireCurrentCollateralPaperApplication(CASE_ID);

        assertEquals("COLLATERAL_PAPER_APPLICATION_REQUIRED", assertThrows(
                BusinessRuleViolationException.class, () -> service.submit(CASE_ID, request())).getErrorCode());
        verify(products, never()).findByProductCode(any());
    }

    @Test
    void createsOneStaffAssistedCollateralAndCompletesCase() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(openCase()));
        when(customers.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "UNVERIFIED")));
        when(products.findByProductCode(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.of(PRODUCT));
        when(checklists.resolveSubmissionInitialState(ProductCode.COLLATERAL_LOAN))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistInitialState(false));
        when(checklists.createSubmissionChecklist(any(), eq(ProductCode.COLLATERAL_LOAN), any()))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistSnapshot(List.of(
                        new LoanDocumentChecklistPort.SubmissionChecklistItemSnapshot(
                                UUID.randomUUID(), DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                                DocumentRequirementStatus.REQUIRED))));
        when(applications.nextApplicationNumberSequence()).thenReturn(9L);
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collaterals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssistedOriginationCaseDto result = service.submit(CASE_ID, request());

        assertEquals("COMPLETED", result.status());
        assertEquals(CUSTOMER_ID, result.customerId());
        ArgumentCaptor<LoanApplication> application = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applications).save(application.capture());
        assertEquals(OriginationChannel.STAFF_ASSISTED, application.getValue().originationChannel());
        assertEquals(LoanApplicationStatus.DOCUMENTS_PENDING, application.getValue().status());
        assertEquals("CL-20260918-000009", application.getValue().applicationNumber());
        assertEquals(result.loanApplicationId(), application.getValue().id());
        ArgumentCaptor<Collateral> collateral = ArgumentCaptor.forClass(Collateral.class);
        verify(collaterals).save(collateral.capture());
        assertEquals(CollateralType.MOTORBIKE, collateral.getValue().collateralType());
        assertEquals("2024 motorbike", collateral.getValue().description());
        assertEquals(new BigDecimal("35000000"), collateral.getValue().estimatedValue());
        assertEquals("Owned by Customer", collateral.getValue().ownershipStatus());
        assertEquals("Normal used condition", collateral.getValue().conditionNote());
        ArgumentCaptor<CollateralLoanVerification> verification =
                ArgumentCaptor.forClass(CollateralLoanVerification.class);
        verify(verifications).save(verification.capture());
        assertEquals(1, verification.getValue().verificationSequence());
        assertEquals(ProductVerificationResult.PENDING_MANUAL_REVIEW,
                verification.getValue().productVerificationResult());
        verify(audits, org.mockito.Mockito.times(2)).publish(any());
    }

    private static AuthenticatedUser staff(UUID customerId, Set<String> permissions) {
        return new AuthenticatedUser(
                STAFF_ID, "staff@meridian.local", "STAFF", customerId,
                Set.of("LOAN_OFFICER"), permissions);
    }

    private static AssistedOriginationCase openCase() {
        return caseValue(ProductCode.COLLATERAL_LOAN, CUSTOMER_ID,
                AssistedOriginationCaseStatus.OPEN, null, null);
    }

    private static AssistedOriginationCase caseValue(
            ProductCode productCode, UUID customerId, AssistedOriginationCaseStatus status,
            LocalDateTime terminalAt, UUID loanApplicationId
    ) {
        return new AssistedOriginationCase(
                CASE_ID, productCode, customerId, status, STAFF_ID,
                NOW.minusDays(1), NOW.minusDays(1), terminalAt, loanApplicationId);
    }

    private static CollateralLoanApplicationRequest request() {
        return new CollateralLoanApplicationRequest(
                new BigDecimal("25000000"), 12,
                new CollateralDetailsRequest(
                        CollateralType.MOTORBIKE, "  2024 motorbike  ",
                        new BigDecimal("35000000"), "  Owned by Customer  ",
                        "  Normal used condition  "));
    }
}

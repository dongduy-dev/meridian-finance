package com.meridian.platform.loan.application.service.unsecured;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
class StartAssistedUnsecuredConsumerLoanServiceTest {

    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STAFF_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 8, 0);
    private static final LoanProduct PRODUCT = new LoanProduct(
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
            "Unsecured Consumer Loan", null, true,
            new BigDecimal("2000000"), new BigDecimal("50000000"));

    @Mock AssistedOriginationCaseRepository cases;
    @Mock LoanIntakeEvidencePort intakeEvidence;
    @Mock LoanProductRepository products;
    @Mock LoanApplicationRepository applications;
    @Mock LoanDocumentChecklistPort checklists;
    @Mock UnsecuredConsumerLoanVerificationRepository verifications;
    @Mock CustomerReadinessPort customers;
    @Mock OutstandingLoanAccountQuery outstandingAccounts;
    @Mock LoanApplicationStatusTransitionRecorder transitions;
    @Mock CurrentUserProvider currentUsers;
    @Mock BusinessAuditPublisher audits;

    private StartAssistedUnsecuredConsumerLoanService service;

    @BeforeEach
    void setUp() {
        service = new StartAssistedUnsecuredConsumerLoanService(
                cases, intakeEvidence, products, applications, checklists, verifications,
                customers, outstandingAccounts, transitions, currentUsers, audits,
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
    void rejectsTerminalWrongProductAndMissingCustomerCasesBeforeEvidence() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.UNSECURED_CONSUMER_LOAN, null, AssistedOriginationCaseStatus.ABANDONED, NOW, null)));
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.COLLATERAL_LOAN, CUSTOMER_ID, AssistedOriginationCaseStatus.OPEN, null, null)));
        assertEquals("ASSISTED_ORIGINATION_PRODUCT_MISMATCH", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());

        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(caseValue(
                ProductCode.UNSECURED_CONSUMER_LOAN, null, AssistedOriginationCaseStatus.OPEN, null, null)));
        assertEquals("ASSISTED_ORIGINATION_CUSTOMER_REQUIRED", assertThrows(
                BusinessStateConflictException.class, () -> service.submit(CASE_ID, request())).getErrorCode());
        verify(intakeEvidence, never()).requireCurrentUclPaperApplication(any());
    }

    @Test
    void requiresCurrentSignedPaperApplicationBeforeLoanEffects() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(openCase()));
        doThrow(new BusinessRuleViolationException(
                "UCL_PAPER_APPLICATION_REQUIRED", "Required"))
                .when(intakeEvidence).requireCurrentUclPaperApplication(CASE_ID);

        assertEquals("UCL_PAPER_APPLICATION_REQUIRED", assertThrows(
                BusinessRuleViolationException.class, () -> service.submit(CASE_ID, request())).getErrorCode());
        verify(products, never()).findByProductCode(any());
    }

    @Test
    void createsStaffAssistedUclAndCompletesCaseWithSameBusinessOutcome() {
        when(cases.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(openCase()));
        when(customers.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "UNVERIFIED")));
        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN)).thenReturn(Optional.of(PRODUCT));
        when(outstandingAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.CLEAR);
        when(checklists.resolveSubmissionInitialState(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistInitialState(false));
        when(checklists.createSubmissionChecklist(any(), eq(ProductCode.UNSECURED_CONSUMER_LOAN), any()))
                .thenReturn(new LoanDocumentChecklistPort.SubmissionChecklistSnapshot(List.of()));
        when(applications.nextApplicationNumberSequence()).thenReturn(9L);
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssistedOriginationCaseDto result = service.submit(CASE_ID, request());

        assertEquals("COMPLETED", result.status());
        assertEquals(CUSTOMER_ID, result.customerId());
        ArgumentCaptor<LoanApplication> application = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applications).save(application.capture());
        assertEquals(OriginationChannel.STAFF_ASSISTED, application.getValue().originationChannel());
        assertEquals("UCL-20260918-000009", application.getValue().applicationNumber());
        assertEquals(result.loanApplicationId(), application.getValue().id());
        ArgumentCaptor<UnsecuredConsumerLoanVerification> verification =
                ArgumentCaptor.forClass(UnsecuredConsumerLoanVerification.class);
        verify(verifications).save(verification.capture());
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
        return caseValue(ProductCode.UNSECURED_CONSUMER_LOAN, CUSTOMER_ID,
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

    private static UnsecuredConsumerLoanApplicationRequest request() {
        return new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("10000000"), 12);
    }
}

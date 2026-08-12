package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionDto;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResubmitCustomerCorrectionServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CORRECTION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RESUBMISSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);

    @Mock private LoanApplicationRepository applications;
    @Mock private LoanCorrectionRepository corrections;
    @Mock private LoanReviewCycleRepository reviewCycles;
    @Mock private LoanDocumentChecklistPort documents;
    @Mock private CustomerReadinessPort readiness;
    @Mock private LoanProductRepository products;
    @Mock private OutstandingLoanAccountQuery outstandingAccounts;
    @Mock private PartnerEligibilityPort partnerEligibility;
    @Mock private SalaryAdvanceLimitRepository salaryLimits;
    @Mock private SalaryAdvanceLimitMovementRepository salaryMovements;
    @Mock private SalaryAdvanceVerificationRepository salaryVerifications;
    @Mock private UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock private LoanApplicationStatusTransitionRecorder transitions;
    @Mock private BusinessAuditPublisher audits;
    @Mock private CurrentUserProvider currentUser;

    private ResubmitCustomerCorrectionService service;
    private LoanApplication application;
    private LoanCorrectionRequest correction;

    @BeforeEach
    void setUp() {
        application = application();
        correction = correction();
        service = new ResubmitCustomerCorrectionService(
                applications,
                corrections,
                reviewCycles,
                documents,
                readiness,
                products,
                outstandingAccounts,
                partnerEligibility,
                salaryLimits,
                salaryMovements,
                salaryVerifications,
                uclVerifications,
                transitions,
                audits,
                currentUser,
                Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC)
        );
        when(currentUser.currentUser()).thenReturn(new AuthenticatedUser(
                USER_ID,
                "customer@meridian.local",
                "CUSTOMER",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                Set.of("loan:correction:resubmit:own")
        ));
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(corrections.findLatestRequestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(correction));
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(correction));
        when(corrections.findTasksByRequestIdForUpdate(CORRECTION_ID))
                .thenReturn(List.of(completedCustomerTask()));
        when(readiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "UNVERIFIED")
        ));
        when(products.findByProductCode(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product()));
        when(documents.readiness(APPLICATION_ID))
                .thenReturn(new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true));
        when(outstandingAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.CLEAR);
        lenient().when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(corrections.saveRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(uclVerifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completedUclDecisionCreatesOneNextPendingCycleAndTargetsSubmitted() {
        UnsecuredConsumerLoanVerification completed = pendingVerification().completeManualReview(
                UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                UUID.randomUUID(),
                NOW.minusHours(1),
                "Replace the income evidence."
        );
        when(uclVerifications.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(completed));

        CorrectionResubmissionDto result = service.resubmit(
                APPLICATION_ID,
                new CorrectionResubmissionRequest(RESUBMISSION_ID)
        );

        assertEquals("SUBMITTED", result.loanApplicationStatus());
        ArgumentCaptor<UnsecuredConsumerLoanVerification> nextCycle =
                ArgumentCaptor.forClass(UnsecuredConsumerLoanVerification.class);
        verify(uclVerifications).save(nextCycle.capture());
        assertEquals(2, nextCycle.getValue().verificationSequence());
        assertEquals(CORRECTION_ID, nextCycle.getValue().sourceCorrectionRequestId());
        assertEquals("PENDING_MANUAL_REVIEW",
                nextCycle.getValue().productVerificationResult().name());
        verifyNoInteractions(partnerEligibility, salaryLimits, salaryMovements, salaryVerifications);
    }

    @Test
    void untouchedPendingUclCycleIsReused() {
        when(uclVerifications.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(pendingVerification()));

        CorrectionResubmissionDto result = service.resubmit(
                APPLICATION_ID,
                new CorrectionResubmissionRequest(RESUBMISSION_ID)
        );

        assertEquals("SUBMITTED", result.loanApplicationStatus());
        verify(uclVerifications, never()).save(any());
        verify(reviewCycles, never()).save(any());
    }

    @Test
    void failedUclCycleCannotBeResubmitted() {
        UnsecuredConsumerLoanVerification failed = pendingVerification().completeManualReview(
                UnsecuredConsumerLoanManualVerificationOutcome.FAILED,
                UUID.randomUUID(),
                NOW.minusHours(1),
                "Verification failed."
        );
        when(uclVerifications.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(failed));

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.resubmit(
                        APPLICATION_ID,
                        new CorrectionResubmissionRequest(RESUBMISSION_ID)
                )
        );

        assertEquals("CORRECTION_RESUBMISSION_NOT_ALLOWED", error.getErrorCode());
        verify(applications, never()).save(any());
        verify(corrections, never()).saveRequest(any());
    }

    @Test
    void outstandingUclDebtBlocksResubmissionBeforeNewCycle() {
        when(outstandingAccounts.inspect(CUSTOMER_ID, ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(OutstandingLoanAccountQuery.GuardResult.OUTSTANDING_EXISTS);

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.resubmit(
                        APPLICATION_ID,
                        new CorrectionResubmissionRequest(RESUBMISSION_ID)
                )
        );

        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", error.getErrorCode());
        verify(uclVerifications, never()).save(any());
        verify(applications, never()).save(any());
    }

    private LoanApplication application() {
        return new LoanApplication(
                APPLICATION_ID,
                CUSTOMER_ID,
                UUID.randomUUID(),
                "UCL-20260812-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                new BigDecimal("5000000.00"),
                6,
                NOW.minusDays(2)
        );
    }

    private LoanProduct product() {
        return new LoanProduct(
                application.loanProductId(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                "Unsecured Consumer Loan",
                null,
                true,
                new BigDecimal("2000000.00"),
                new BigDecimal("50000000.00")
        );
    }

    private LoanCorrectionRequest correction() {
        return new LoanCorrectionRequest(
                CORRECTION_ID,
                APPLICATION_ID,
                null,
                "COMPLETE_PRODUCT_VERIFICATION",
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                UUID.randomUUID(),
                LoanCorrectionRequestStatus.OPEN,
                null,
                NOW.minusHours(2),
                null,
                null,
                null
        );
    }

    private LoanCorrectionTask completedCustomerTask() {
        return new LoanCorrectionTask(
                UUID.randomUUID(),
                CORRECTION_ID,
                1,
                LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT,
                DocumentType.INCOME_PROOF,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Replace the income proof.",
                null,
                LoanCorrectionTaskStatus.COMPLETED,
                USER_ID,
                UUID.randomUUID(),
                NOW.minusMinutes(30),
                NOW.minusHours(2)
        );
    }

    private UnsecuredConsumerLoanVerification pendingVerification() {
        return UnsecuredConsumerLoanVerification.pendingManualReview(
                UUID.randomUUID(),
                new LoanApplication(
                        application.id(),
                        application.customerId(),
                        application.loanProductId(),
                        application.applicationNumber(),
                        application.productCode(),
                        application.productType(),
                        LoanApplicationStatus.SUBMITTED,
                        application.requestedAmount(),
                        application.requestedTermMonths(),
                        application.submittedAt()
                ),
                NOW.minusDays(1)
        );
    }
}

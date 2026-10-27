package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase.*;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryApprovalLoanCaseServiceTest {

    @Mock LoanApplicationRepository applications;
    @Mock SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    @Mock UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock CollateralLoanVerificationRepository collateralVerifications;
    @Mock LoanReviewCycleRepository reviewCycles;
    @Mock LoanDocumentChecklistPort documents;

    @ParameterizedTest
    @EnumSource(OriginationChannel.class)
    void caseCarriesTheExactApplicationsChannelAndCustomerLinkageOnlyInternally(OriginationChannel channel) {
        UUID applicationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        LoanApplication application = new LoanApplication(
                applicationId, customerId, UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                channel,
                LoanApplicationStatus.APPROVAL_PENDING, BigDecimal.TEN, 6,
                LocalDateTime.of(2026, 9, 6, 8, 0));
        when(applications.findById(applicationId)).thenReturn(java.util.Optional.of(application));
        when(documents.readiness(applicationId)).thenReturn(
                new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true));
        var verification = org.mockito.Mockito.mock(
                com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification.class);
        when(verification.productVerificationResult()).thenReturn(
                com.meridian.platform.loan.domain.model.ProductVerificationResult.VERIFIED);
        when(uclVerifications.findLatestByLoanApplicationId(applicationId)).thenReturn(java.util.Optional.of(verification));
        var adapter = new QueryApprovalLoanCaseService(applications, salaryAdvanceVerifications,
                uclVerifications, collateralVerifications, reviewCycles, documents);

        var result = adapter.findCase(applicationId).orElseThrow();
        assertEquals(applicationId, result.loanApplicationId());
        assertEquals(customerId, result.customerId());
        assertEquals(channel.name(), result.originationChannel());
    }

    @Test
    void decisionQueueUsesExactServerSideStatusProductAndPaging() {
        UUID applicationId = UUID.randomUUID();
        LoanApplication application = new LoanApplication(
                applicationId, UUID.randomUUID(), UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.APPROVAL_PENDING, BigDecimal.TEN, 6,
                LocalDateTime.of(2026, 9, 6, 8, 0)
        );
        when(applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.APPROVAL_PENDING,
                2,
                25
        )).thenReturn(new LoanApplicationRepository.StaffPage(2, 25, 51, 3, List.of(application)));
        QueryApprovalLoanCaseService adapter = new QueryApprovalLoanCaseService(
                applications, salaryAdvanceVerifications, uclVerifications,
                collateralVerifications, reviewCycles, documents
        );

        var result = adapter.findDecisionQueue("UNSECURED_CONSUMER_LOAN", 2, 25);

        assertEquals(applicationId, result.items().getFirst().loanApplicationId());
        assertEquals(51, result.totalElements());
        verify(applications).findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.APPROVAL_PENDING,
                2,
                25
        );
    }

    @ParameterizedTest
    @EnumSource(ProductCode.class)
    void preservesEveryCaseFieldReadinessCombinationLatestCycleAndProductRestrictedOptions(ProductCode product) {
        var application = application(product);
        UUID id = application.id();
        UUID officer = UUID.randomUUID();
        var cycle = new LoanApplicationReviewCycle(UUID.randomUUID(), id, 4, officer,
                LoanReviewCycleStatus.COMPLETED, application.submittedAt(), application.submittedAt().plusHours(1));
        when(applications.findById(id)).thenReturn(Optional.of(application));
        when(reviewCycles.findLatestByLoanApplicationId(id)).thenReturn(Optional.of(cycle));
        var targets = java.util.Arrays.stream(LoanDocumentType.values()).map(type ->
                new LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot(UUID.randomUUID(), type,
                        LoanDocumentChecklistPort.RequirementStatus.REQUIRED, UUID.randomUUID())).toList();
        when(documents.currentVersionTargets(id)).thenReturn(targets);
        List<String> codes = switch (product) {
            case SALARY_ADVANCE -> List.of("RECENT_PAYSLIP");
            case UNSECURED_CONSUMER_LOAN -> List.of("INCOME_PROOF", "BANK_STATEMENT", "EMPLOYMENT_PROOF");
            case COLLATERAL_LOAN -> List.of("COLLATERAL_OWNERSHIP_EVIDENCE");
        };
        var expectedOptions = targets.stream().filter(t -> codes.contains(t.documentType().name()))
                .map(t -> new CorrectionOptionSnapshot(t.documentType().name(), t.checklistItemId(),
                        t.currentDocumentVersionId(), List.of("DOCUMENT_REPLACEMENT", "DOCUMENT_REVIEW"))).toList();
        var service = service();
        for (var verification : ProductVerificationResult.values()) {
            verification(product, id, verification);
            for (boolean uploadComplete : new boolean[]{false, true}) {
                for (boolean processingReady : new boolean[]{false, true}) {
                    when(documents.readiness(id)).thenReturn(
                            new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(uploadComplete, processingReady));
                    var expected = new CaseSnapshot(id, application.customerId(), application.applicationNumber(),
                            product.name(), application.productType().name(), application.requestedAmount(),
                            application.requestedTermMonths(), "APPROVAL_PENDING", application.submittedAt(),
                            application.originationChannel().name(), new DocumentReadinessSnapshot(uploadComplete, processingReady),
                            new ProductReadinessSnapshot(verification.name(), verification == ProductVerificationResult.VERIFIED && processingReady),
                            new ReviewCycleSnapshot(cycle.id(), 4, officer, "COMPLETED", cycle.startedAt(), cycle.endedAt()),
                            expectedOptions);
                    var actual = service.findCase(id).orElseThrow();
                    assertEquals(expected, actual);
                    assertEquals(codes, actual.correctionOptions().stream().map(CorrectionOptionSnapshot::documentType).toList());
                    assertThrows(UnsupportedOperationException.class, () -> actual.correctionOptions().clear());
                    assertThrows(UnsupportedOperationException.class, () -> actual.correctionOptions().getFirst().allowedScopes().clear());
                }
            }
        }
        verify(reviewCycles, never()).findActiveByLoanApplicationIdForUpdate(any());
    }

    @ParameterizedTest
    @EnumSource(ProductCode.class)
    void missingVerificationFailsWithTheOriginalConflictBeforeCycleOrTargetReads(ProductCode product) {
        var application = application(product);
        when(applications.findById(application.id())).thenReturn(Optional.of(application));
        when(documents.readiness(application.id())).thenReturn(new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true));
        var failure = assertThrows(BusinessStateConflictException.class, () -> service().findCase(application.id()));
        assertEquals("SYSTEM_STATE_CONFLICT", failure.getErrorCode());
        assertEquals("Authoritative product verification evidence is inconsistent.", failure.getMessage());
        verifyNoInteractions(reviewCycles);
        verify(documents, never()).currentVersionTargets(any());
    }

    @Test
    void missingApplicationDoesNotReadAnyEvidenceAndProviderErrorsKeepTheirIdentity() {
        UUID id = UUID.randomUUID();
        assertTrue(service().findCase(id).isEmpty());
        verifyNoInteractions(documents, reviewCycles, salaryAdvanceVerifications, uclVerifications, collateralVerifications);
        var failure = new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", "unavailable");
        when(applications.findById(id)).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessStateConflictException.class, () -> service().findCase(id)));
    }

    @Test
    void salaryAdvanceOffersMissingPayslipOnlyWhenNoCurrentPayslipTargetExistsAndAllowsMissingCycle() {
        var application = application(ProductCode.SALARY_ADVANCE);
        when(applications.findById(application.id())).thenReturn(Optional.of(application));
        verification(application.productCode(), application.id(), ProductVerificationResult.VERIFIED);
        when(documents.readiness(application.id())).thenReturn(new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true));
        assertEquals(List.of(new CorrectionOptionSnapshot("RECENT_PAYSLIP", null, null,
                List.of("SUPPORTING_DOCUMENT_UPLOAD"))), service().findCase(application.id()).orElseThrow().correctionOptions());
        assertNull(service().findCase(application.id()).orElseThrow().currentReviewCycle());
    }

    @Test
    void unfilteredQueuePreservesEmptyOutOfRangePagesAndRepositoryFailures() {
        when(applications.findStaffPage(null, LoanApplicationStatus.APPROVAL_PENDING, 9, 5))
                .thenReturn(new LoanApplicationRepository.StaffPage(9, 5, 2, 1, List.of()));
        assertEquals(new QueuePageSnapshot(9, 5, 2, 1, List.of()), service().findDecisionQueue(null, 9, 5));
        var failure = new IllegalArgumentException("invalid page");
        when(applications.findStaffPage(null, LoanApplicationStatus.APPROVAL_PENDING, -1, 5)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> service().findDecisionQueue(null, -1, 5)));
        assertThrows(IllegalArgumentException.class, () -> service().findDecisionQueue("UNKNOWN", 0, 5));
    }

    private QueryApprovalLoanCaseService service() {
        return new QueryApprovalLoanCaseService(applications, salaryAdvanceVerifications,
                uclVerifications, collateralVerifications, reviewCycles, documents);
    }

    private static LoanApplication application(ProductCode product) {
        var type = switch (product) {
            case SALARY_ADVANCE -> ProductType.SALARY_BASED;
            case UNSECURED_CONSUMER_LOAN -> ProductType.UNSECURED;
            case COLLATERAL_LOAN -> ProductType.SECURED;
        };
        return new LoanApplication(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "APP-EXACT", product,
                type, OriginationChannel.CUSTOMER_DIGITAL, LoanApplicationStatus.APPROVAL_PENDING,
                new BigDecimal("1234567.89"), 9, LocalDateTime.of(2026, 10, 1, 8, 9, 10, 123456000));
    }

    private void verification(ProductCode product, UUID id, ProductVerificationResult result) {
        switch (product) {
            case SALARY_ADVANCE -> {
                var evidence = mock(com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification.class);
                when(evidence.productVerificationResult()).thenReturn(result);
                when(salaryAdvanceVerifications.findByLoanApplicationId(id)).thenReturn(Optional.of(evidence));
            }
            case UNSECURED_CONSUMER_LOAN -> {
                var evidence = mock(com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification.class);
                when(evidence.productVerificationResult()).thenReturn(result);
                when(uclVerifications.findLatestByLoanApplicationId(id)).thenReturn(Optional.of(evidence));
            }
            case COLLATERAL_LOAN -> {
                var evidence = mock(com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification.class);
                when(evidence.productVerificationResult()).thenReturn(result);
                when(collateralVerifications.findLatestByLoanApplicationId(id)).thenReturn(Optional.of(evidence));
            }
        }
    }
}

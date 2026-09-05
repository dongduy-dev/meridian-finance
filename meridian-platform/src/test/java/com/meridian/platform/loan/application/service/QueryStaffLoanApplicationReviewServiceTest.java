package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffLoanApplicationReviewServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID REVIEW_CYCLE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 9, 0);

    @Mock LoanApplicationRepository applications;
    @Mock SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    @Mock UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock CollateralLoanVerificationRepository collateralVerifications;
    @Mock LoanReviewCycleRepository reviewCycles;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffLoanApplicationReviewService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffLoanApplicationReviewService(
                applications,
                salaryAdvanceVerifications,
                uclVerifications,
                collateralVerifications,
                reviewCycles,
                documents,
                currentUserProvider
        );
    }

    @Test
    void exposesBackendDerivedStartEligibilityWithoutAReviewCycle() {
        LoanApplication application = application(LoanApplicationStatus.SUBMITTED);
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documents.readiness(APPLICATION_ID)).thenReturn(
                new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true)
        );
        when(uclVerifications.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(
                Optional.of(verification(ProductVerificationResult.VERIFIED))
        );
        when(reviewCycles.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        var result = service.query(APPLICATION_ID);

        assertTrue(result.reviewStartAvailable());
        assertTrue(result.productReadiness().readyForReview());
        assertEquals("VERIFIED", result.productReadiness().productVerificationResult());
        assertNull(result.currentReviewCycle());
    }

    @Test
    void returnsAuthoritativeActiveCycleAndFailsClosedForActions() {
        LoanApplication application = application(LoanApplicationStatus.UNDER_REVIEW);
        LoanApplicationReviewCycle cycle = new LoanApplicationReviewCycle(
                REVIEW_CYCLE_ID,
                APPLICATION_ID,
                2,
                LoanReviewCycleStatus.ACTIVE,
                NOW,
                null
        );
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documents.readiness(APPLICATION_ID)).thenReturn(
                new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true)
        );
        when(uclVerifications.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(
                Optional.of(verification(ProductVerificationResult.VERIFIED))
        );
        when(reviewCycles.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(cycle));

        var result = service.query(APPLICATION_ID);

        assertFalse(result.reviewStartAvailable());
        assertEquals(REVIEW_CYCLE_ID, result.currentReviewCycle().reviewCycleId());
        assertEquals("ACTIVE", result.currentReviewCycle().status());
    }

    @Test
    void pendingVerificationAndTerminalStateNeverAdvertiseReviewStart() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(
                Optional.of(application(LoanApplicationStatus.VERIFICATION_FAILED))
        );
        when(documents.readiness(APPLICATION_ID)).thenReturn(
                new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(true, true)
        );
        when(uclVerifications.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(
                Optional.of(verification(ProductVerificationResult.FAILED))
        );
        when(reviewCycles.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        var result = service.query(APPLICATION_ID);

        assertFalse(result.reviewStartAvailable());
        assertFalse(result.productReadiness().readyForReview());
    }

    @Test
    void roleNameAndNonReviewPermissionsDoNotGrantReadAccess() {
        for (Set<String> permissions : java.util.List.of(
                Set.of("loan:read"),
                Set.of("approval:recommend"),
                Set.of("loan:review:all")
        )) {
            when(currentUserProvider.currentUser()).thenReturn(staff(permissions));
            assertThrows(AuthorizationException.class, () -> service.query(APPLICATION_ID));
        }
    }

    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UCL-20260905-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                new BigDecimal("8000000"),
                6,
                NOW.minusHours(2)
        );
    }

    private static UnsecuredConsumerLoanVerification verification(ProductVerificationResult result) {
        boolean pending = result == ProductVerificationResult.PENDING_MANUAL_REVIEW;
        return new UnsecuredConsumerLoanVerification(
                UUID.randomUUID(),
                APPLICATION_ID,
                1,
                null,
                result,
                NOW.minusHours(1),
                pending ? null : UUID.randomUUID(),
                pending ? null : NOW,
                pending ? null : "Restricted note"
        );
    }

    private static AuthenticatedUser staff(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "staff@meridian.test",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                permissions
        );
    }
}

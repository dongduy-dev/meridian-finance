package com.meridian.platform.loan.application.service;

import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffLoanApplicationVerificationServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID VERIFICATION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID CHECKLIST_ITEM_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID VERSION_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 9, 5, 8, 0);

    @Mock LoanApplicationRepository applications;
    @Mock SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    @Mock UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock CollateralLoanVerificationRepository collateralVerifications;
    @Mock CollateralRepository collaterals;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffLoanApplicationVerificationService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffLoanApplicationVerificationService(
                applications,
                salaryAdvanceVerifications,
                uclVerifications,
                collateralVerifications,
                collaterals,
                documents,
                currentUserProvider
        );
    }

    @Test
    void returnsLatestImmutableSalaryAdvanceSnapshotsWithoutManualActions() {
        LoanApplication application = application(
                ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED, LoanApplicationStatus.SUBMITTED
        );
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documents.readiness(APPLICATION_ID)).thenReturn(readiness(true, true));
        when(salaryAdvanceVerifications.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(
                new SalaryAdvanceVerification(
                        VERIFICATION_ID,
                        APPLICATION_ID,
                        2,
                        UUID.randomUUID(),
                        CUSTOMER_ID,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                        ProductVerificationResult.VERIFIED,
                        new BigDecimal("10000000"),
                        new BigDecimal("2000000"),
                        new BigDecimal("3000000"),
                        new BigDecimal("5000000"),
                        SUBMITTED_AT.plusHours(1)
                )
        ));

        var result = service.query(APPLICATION_ID);
        var verification = assertInstanceOf(
                com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto
                        .SalaryAdvanceVerificationDto.class,
                result.productVerification()
        );

        assertEquals(2, verification.verificationSequence());
        assertEquals(new BigDecimal("5000000"), verification.availableLimitSnapshot());
        assertFalse(result.actions().startAvailable());
        assertFalse(result.actions().completeAvailable());
        assertTrue(result.correctionTargets().isEmpty());
    }

    @Test
    void returnsDeterministicUclHistoryAndPurposeLimitedCorrectionTargets() {
        LoanApplication application = application(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                LoanApplicationStatus.VERIFICATION_PENDING
        );
        var first = completedUcl(1, ProductVerificationResult.REQUIRES_MORE_INFORMATION);
        var current = pendingUcl(2);
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documents.readiness(APPLICATION_ID)).thenReturn(readiness(true, true));
        when(uclVerifications.findAllByLoanApplicationIdOrderByVerificationSequenceAsc(APPLICATION_ID))
                .thenReturn(List.of(first, current));
        when(documents.currentVersionTargets(APPLICATION_ID)).thenReturn(List.of(
                new LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot(
                        CHECKLIST_ITEM_ID,
                        DocumentType.BANK_STATEMENT,
                        DocumentRequirementStatus.REQUIRED,
                        VERSION_ID
                ),
                new LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot(
                        UUID.randomUUID(),
                        DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                        DocumentRequirementStatus.REQUIRED,
                        UUID.randomUUID()
                )
        ));

        var result = service.query(APPLICATION_ID);
        var verification = assertInstanceOf(
                com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto
                        .ManualVerificationDto.class,
                result.productVerification()
        );

        assertEquals(2, verification.currentCycle().verificationSequence());
        assertEquals(List.of(1, 2), verification.history().stream()
                .map(item -> item.verificationSequence()).toList());
        assertNull(verification.collateral());
        assertTrue(result.actions().completeAvailable());
        assertEquals(1, result.correctionTargets().size());
        assertEquals("BANK_STATEMENT", result.correctionTargets().getFirst().documentType());
    }

    @Test
    void returnsCollateralSnapshotAndFailsClosedForInconsistentCollateralCardinality() {
        LoanApplication application = application(
                ProductCode.COLLATERAL_LOAN, ProductType.SECURED, LoanApplicationStatus.SUBMITTED
        );
        var current = pendingCollateral(1);
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documents.readiness(APPLICATION_ID)).thenReturn(readiness(true, true));
        when(collateralVerifications.findAllByLoanApplicationIdOrderByVerificationSequenceAsc(APPLICATION_ID))
                .thenReturn(List.of(current));
        when(collaterals.findByLoanApplicationId(APPLICATION_ID)).thenReturn(List.of(collateral()));
        when(documents.currentVersionTargets(APPLICATION_ID)).thenReturn(List.of(
                new LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot(
                        CHECKLIST_ITEM_ID,
                        DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                        DocumentRequirementStatus.REQUIRED,
                        VERSION_ID
                )
        ));

        var result = service.query(APPLICATION_ID);
        var verification = assertInstanceOf(
                com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto
                        .ManualVerificationDto.class,
                result.productVerification()
        );
        assertEquals("CAR", verification.collateral().collateralType());
        assertEquals(VERIFICATION_ID, verification.currentCycle().verificationId());
        assertTrue(result.actions().startAvailable());

        when(collaterals.findByLoanApplicationId(APPLICATION_ID)).thenReturn(List.of());
        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class, () -> service.query(APPLICATION_ID)
        );
        assertEquals("SYSTEM_STATE_CONFLICT", error.getErrorCode());
    }

    @Test
    void exactAuthorityAndStaffShapeAreRequiredBeforeReadingApplicationState() {
        for (AuthenticatedUser denied : List.of(
                staff(Set.of("loan:read")),
                staff(Set.of("approval:recommend")),
                staff(Set.of("loan:review:all")),
                customer(Set.of("loan:review")),
                customerShapedStaff(Set.of("loan:review"))
        )) {
            when(currentUserProvider.currentUser()).thenReturn(denied);
            AuthorizationException error = assertThrows(
                    AuthorizationException.class, () -> service.query(APPLICATION_ID)
            );
            assertEquals("LOAN_REVIEW_ACCESS_DENIED", error.getErrorCode());
        }
    }

    @Test
    void missingApplicationUsesSafeNotFoundContract() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:review")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
        EntityNotFoundException error = assertThrows(
                EntityNotFoundException.class, () -> service.query(APPLICATION_ID)
        );
        assertEquals("LOAN_APPLICATION_NOT_FOUND", error.getErrorCode());
    }

    private static LoanApplication application(
            ProductCode productCode,
            ProductType productType,
            LoanApplicationStatus status
    ) {
        return new LoanApplication(
                APPLICATION_ID,
                CUSTOMER_ID,
                UUID.randomUUID(),
                productCode == ProductCode.SALARY_ADVANCE ? "SA-20260905-000001" : "LOAN-20260905-000001",
                productCode,
                productType,
                status,
                new BigDecimal("6000000"),
                6,
                SUBMITTED_AT
        );
    }

    private static UnsecuredConsumerLoanVerification completedUcl(
            int sequence,
            ProductVerificationResult result
    ) {
        return new UnsecuredConsumerLoanVerification(
                UUID.randomUUID(),
                APPLICATION_ID,
                sequence,
                sequence == 1 ? null : UUID.randomUUID(),
                result,
                SUBMITTED_AT.plusHours(sequence),
                UUID.randomUUID(),
                SUBMITTED_AT.plusHours(sequence + 1L),
                "Restricted note"
        );
    }

    private static UnsecuredConsumerLoanVerification pendingUcl(int sequence) {
        return new UnsecuredConsumerLoanVerification(
                VERIFICATION_ID,
                APPLICATION_ID,
                sequence,
                sequence == 1 ? null : UUID.randomUUID(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                SUBMITTED_AT.plusHours(sequence),
                null,
                null,
                null
        );
    }

    private static CollateralLoanVerification pendingCollateral(int sequence) {
        return new CollateralLoanVerification(
                VERIFICATION_ID,
                APPLICATION_ID,
                sequence,
                sequence == 1 ? null : UUID.randomUUID(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                SUBMITTED_AT.plusHours(sequence),
                null,
                null,
                null
        );
    }

    private static Collateral collateral() {
        return new Collateral(
                UUID.randomUUID(),
                APPLICATION_ID,
                CollateralType.CAR,
                "Customer vehicle",
                new BigDecimal("25000000"),
                "Customer-owned",
                "Operational condition",
                SUBMITTED_AT
        );
    }

    private static LoanDocumentChecklistPort.ChecklistReadinessSnapshot readiness(
            boolean uploadComplete,
            boolean processingReady
    ) {
        return new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(uploadComplete, processingReady);
    }

    private static AuthenticatedUser staff(Set<String> permissions) {
        return actor("STAFF", null, permissions);
    }

    private static AuthenticatedUser customer(Set<String> permissions) {
        return actor("CUSTOMER", CUSTOMER_ID, permissions);
    }

    private static AuthenticatedUser customerShapedStaff(Set<String> permissions) {
        return actor("STAFF", CUSTOMER_ID, permissions);
    }

    private static AuthenticatedUser actor(String userType, UUID customerId, Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "actor@meridian.test",
                userType,
                customerId,
                Set.of("LOAN_OFFICER"),
                permissions
        );
    }
}

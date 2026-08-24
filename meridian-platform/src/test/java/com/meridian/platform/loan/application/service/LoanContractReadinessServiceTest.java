package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.*;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.loan.domain.model.collateral.*;
import com.meridian.platform.loan.domain.model.salaryadvance.*;
import com.meridian.platform.loan.domain.model.unsecured.*;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanContractReadinessServiceTest {
    @Mock LoanApplicationRepository applications;
    @Mock ApprovedOfferRepository offers;
    @Mock LoanContractRepository contracts;
    @Mock LoanCorrectionRepository corrections;
    @Mock LoanDocumentChecklistPort documents;
    @Mock ContractBankAccountPort bankAccounts;
    @Mock DisbursementBankAccountProtector protector;
    @Mock SalaryAdvanceVerificationRepository verifications;
    @Mock UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock CollateralLoanVerificationRepository collateralVerifications;
    @Mock SalaryAdvanceLimitRepository limits;
    @Mock SalaryAdvanceLimitMovementRepository movements;
    @Mock LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock BusinessAuditPublisher audit;
    @Mock CurrentUserProvider users;
    LoanContractReadinessService service;

    @BeforeEach void setUp() {
        service = new LoanContractReadinessService(applications, offers, contracts, corrections, documents,
                bankAccounts, protector, verifications, uclVerifications, collateralVerifications,
                limits, movements,
                transitionRecorder, audit, users,
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test void firstPreparationCapturesPrimaryAccountWithoutProfileReadinessAndReplaysIdempotently() {
        Fixture f = fixture();
        stubPreparation(f);
        UUID requestId = UUID.randomUUID();
        when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanContract prepared = service.prepare(new PrepareLoanContractUseCase.Command(requestId, f.application.id(), 0, null));

        assertEquals(LoanContractStatus.PREPARED, prepared.status());
        assertEquals(f.accountId, prepared.disbursementBankAccount().sourceBankAccountId());
        assertTrue(f.sensitive.cleared());
        verify(bankAccounts).capturePrimaryActive(f.application.customerId());
        verify(documents).isProcessingReady(f.application.id());
        when(contracts.findByPreparationRequestId(requestId)).thenReturn(Optional.of(prepared));
        assertSame(prepared, service.prepare(new PrepareLoanContractUseCase.Command(requestId, f.application.id(), 0, null)));
        verify(contracts, times(1)).save(any());
    }

    @Test void uclPreparationCopiesAcceptedOfferWithoutSalaryExposureWork() {
        UclFixture f = uclFixture();
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id()))
                .thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(uclVerifications.findLatestByLoanApplicationId(f.application.id()))
                .thenReturn(Optional.of(f.verification));
        when(bankAccounts.capturePrimaryActive(f.application.customerId())).thenReturn(f.sensitive);
        when(protector.protect(any(byte[].class), any())).thenReturn(
                new ProtectedBankAccountEnvelope(
                        "AES-256-GCM", "v1", new byte[12], new byte[]{1},
                        "DISBURSEMENT_ACCOUNT_V1"
                )
        );
        when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanContract prepared = service.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), f.application.id(), 0, null
        ));

        assertEquals(f.offer.financialTerms(), prepared.financialTerms());
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT,
                prepared.financialTerms().repaymentMethod());
        assertEquals(f.offer.repaymentItems().size(), prepared.repaymentItems().size());
        assertTrue(f.sensitive.cleared());
        verifyNoInteractions(verifications, limits, movements);
    }

    @Test void uclPreparationRejectsInvalidVerificationBeforeSensitiveCapture() {
        UclFixture f = uclFixture();
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id()))
                .thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(uclVerifications.findLatestByLoanApplicationId(f.application.id())).thenReturn(Optional.empty());

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), f.application.id(), 0, null
                ))
        );

        assertEquals("UCL_VERIFICATION_INVALID", error.getErrorCode());
        verifyNoInteractions(bankAccounts, protector, verifications, limits, movements);
        verify(contracts, never()).save(any());
    }

    @Test void collateralPreparationCopiesAcceptedOfferWithoutSalaryExposureWork() {
        CollateralFixture f = collateralFixture();
        stubCollateralPreparation(f);
        when(collateralVerifications.findLatestByLoanApplicationId(f.application.id()))
                .thenReturn(Optional.of(f.verification));
        when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanContract prepared = service.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), f.application.id(), 0, null
        ));

        assertEquals(f.offer.financialTerms(), prepared.financialTerms());
        assertEquals(new BigDecimal("0.015000"),
                prepared.financialTerms().flatMonthlyInterestRate());
        assertEquals(f.offer.repaymentItems().size(), prepared.repaymentItems().size());
        assertEquals(
                f.offer.repaymentItems().stream()
                        .map(item -> List.of(
                                item.principalDue(), item.interestDue(), item.feeDue(), item.totalDue()
                        ))
                        .toList(),
                prepared.repaymentItems().stream()
                        .map(item -> List.of(
                                item.principalDue(), item.interestDue(), item.feeDue(), item.totalDue()
                        ))
                        .toList()
        );
        assertTrue(f.sensitive.cleared());
        verifyNoInteractions(verifications, uclVerifications, limits, movements);
    }

    @Test void collateralPreparationRejectsEveryInvalidVerificationBeforeSensitiveCapture() {
        CollateralFixture f = collateralFixture();
        stubCollateralPreparationWithoutVerification(f);
        List<Optional<CollateralLoanVerification>> invalid = List.of(
                Optional.empty(),
                Optional.of(collateralVerification(
                        f.application.id(), ProductVerificationResult.PENDING_MANUAL_REVIEW
                )),
                Optional.of(collateralVerification(
                        f.application.id(), ProductVerificationResult.FAILED
                )),
                Optional.of(collateralVerification(
                        f.application.id(), ProductVerificationResult.REQUIRES_MORE_INFORMATION
                ))
        );

        for (Optional<CollateralLoanVerification> verification : invalid) {
            when(collateralVerifications.findLatestByLoanApplicationId(f.application.id()))
                    .thenReturn(verification);
            BusinessStateConflictException error = assertThrows(
                    BusinessStateConflictException.class,
                    () -> service.prepare(new PrepareLoanContractUseCase.Command(
                            UUID.randomUUID(), f.application.id(), 0, null
                    ))
            );
            assertEquals("COLLATERAL_VERIFICATION_INVALID", error.getErrorCode());
        }

        verifyNoInteractions(bankAccounts, protector, verifications, uclVerifications, limits, movements);
        verify(contracts, never()).save(any());
    }

    @Test void preparationRejectsDocumentsNotReadyBeforeSensitiveCapture() {
        Fixture f = fixture();
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(false);
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals("DOCUMENTS_NOT_PROCESSING_READY", error.getErrorCode());
        verifyNoInteractions(bankAccounts);
    }

    @Test void regenerationRequiresExactVersionAndOnlyControlledReason() {
        Fixture f = fixture(); LoanContract current = contract(f, 1, LoanContractStatus.PREPARED);
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(current));
        BusinessStateConflictException stale = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0,
                        ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)));
        assertEquals("CONTRACT_VERSION_STALE", stale.getErrorCode());
    }

    @Test void preparationRequestReuseWithDifferentLogicalIdentityIsRejected() {
        Fixture f = fixture();
        LoanContract replay = contract(f, 1, LoanContractStatus.PREPARED);
        AuthenticatedUser originalActor = new AuthenticatedUser(replay.preparedByUserId(),
                "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of());
        when(users.currentUser()).thenReturn(originalActor);
        when(contracts.findByPreparationRequestId(replay.preparationRequestId()))
                .thenReturn(Optional.of(replay));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(
                        replay.preparationRequestId(), UUID.randomUUID(), 0, null)));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verifyNoInteractions(applications);
    }

    @Test void acknowledgedVersionReplaysAfterAnotherVersionBecomesCurrent() {
        Fixture f = fixture();
        LoanContract acknowledgedV1 = contract(f, 1, LoanContractStatus.ACKNOWLEDGED);
        UUID requestId = acknowledgedV1.acknowledgmentRequestId();
        AuthenticatedUser customer = new AuthenticatedUser(
                acknowledgedV1.acknowledgedByUserId(), "owner@meridian.test", "CUSTOMER",
                f.application.customerId(), Set.of("CUSTOMER"), Set.of());
        when(users.currentUser()).thenReturn(customer);
        when(contracts.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.of(acknowledgedV1));
        LoanContract replay = service.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                requestId, f.application.id(), 1));
        assertSame(acknowledgedV1, replay);
        var lockOrder = inOrder(contracts);
        lockOrder.verify(contracts).acquireAcknowledgmentRequestLock(requestId);
        lockOrder.verify(contracts).findByAcknowledgmentRequestId(requestId);
        verifyNoInteractions(applications);
        BusinessStateConflictException reused = assertThrows(BusinessStateConflictException.class,
                () -> service.acknowledge(new AcknowledgeLoanContractUseCase.Command(requestId, f.application.id(), 2)));
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.getErrorCode());
        verify(contracts, never()).save(any());
    }
    @Test void acknowledgmentRequiresAuthenticatedCustomerOwnership() {
        Fixture f = fixture(); LoanContract current = contract(f, 1, LoanContractStatus.PREPARED);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "c@meridian.test", "CUSTOMER",
                UUID.randomUUID(), Set.of("CUSTOMER"), Set.of()));
        when(contracts.findByAcknowledgmentRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        assertThrows(AuthorizationException.class, () -> service.acknowledge(
                new AcknowledgeLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 1)));
        verify(contracts, never()).save(any());
    }

    @Test void currentContractQueryEnforcesCustomerOwnershipAndAccountingAuthority() {
        Fixture f = fixture();
        LoanContract current = contract(f, 1, LoanContractStatus.PREPARED);
        when(applications.findById(f.application.id())).thenReturn(Optional.of(f.application));
        when(contracts.findCurrentByApplicationId(f.application.id())).thenReturn(Optional.of(current));

        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "owner@meridian.test", "CUSTOMER",
                f.application.customerId(), Set.of("CUSTOMER"), Set.of("loan:read:own")
        ));
        assertSame(current, service.findCurrent(f.application.id()).orElseThrow());

        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "other@meridian.test", "CUSTOMER",
                UUID.randomUUID(), Set.of("CUSTOMER"), Set.of("loan:read:own")
        ));
        AuthorizationException denial = assertThrows(
                AuthorizationException.class,
                () -> service.findCurrent(f.application.id())
        );
        assertEquals("LOAN_APPLICATION_ACCESS_DENIED", denial.getErrorCode());

        when(users.currentUser()).thenReturn(staff());
        assertSame(current, service.findCurrent(f.application.id()).orElseThrow());
    }

    @Test void readinessReportsInactiveCapturedAccountAndReleasedReservation() {
        Fixture f = fixture(); LoanContract acknowledged = contract(f, 1, LoanContractStatus.ACKNOWLEDGED);
        when(users.currentUser()).thenReturn(staff());
        when(applications.findById(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationId(f.application.id())).thenReturn(Optional.of(acknowledged));
        when(bankAccounts.inspectCaptured(f.application.customerId(), f.accountId))
                .thenReturn(new ContractBankAccountPort.ContractBankAccountState(true, true, false));
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(corrections.existsActiveRequestByApplicationId(f.application.id())).thenReturn(false);
        when(verifications.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.verification));
        when(limits.findById(f.limit.id())).thenReturn(Optional.of(f.limit));
        when(movements.existsByLoanApplicationIdAndMovementType(f.application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED)).thenReturn(true);
        QueryContractReadinessUseCase.Snapshot snapshot = service.query(f.application.id(), null);
        assertTrue(snapshot.blockers().contains(ContractReadinessBlockerCode.CAPTURED_ACCOUNT_INACTIVE));
        assertTrue(snapshot.blockers().contains(ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_RELEASED));
        assertFalse(snapshot.blockers().contains(ContractReadinessBlockerCode.CONTRACT_VERSION_STALE));
        verify(bankAccounts).inspectCaptured(f.application.customerId(), f.accountId);
        verify(bankAccounts, never()).inspectCapturedForUpdate(any(), any());
    }

    @Test void uclReadinessRequiresVerifiedEvidenceWithoutSalaryReservationAccess() {
        UclFixture f = uclFixture();
        LoanContract acknowledged = uclContract(f, LoanContractStatus.ACKNOWLEDGED);
        when(users.currentUser()).thenReturn(staff());
        when(applications.findById(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationId(f.application.id()))
                .thenReturn(Optional.of(acknowledged));
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(corrections.existsActiveRequestByApplicationId(f.application.id())).thenReturn(false);
        when(bankAccounts.inspectCaptured(f.application.customerId(), f.accountId))
                .thenReturn(new ContractBankAccountPort.ContractBankAccountState(true, true, true));
        when(uclVerifications.findLatestByLoanApplicationId(f.application.id()))
                .thenReturn(Optional.of(f.verification));

        QueryContractReadinessUseCase.Snapshot ready = service.query(f.application.id(), 1);

        assertTrue(ready.ready());
        assertTrue(ready.blockers().isEmpty());
        verifyNoInteractions(verifications, limits, movements);

        reset(uclVerifications);
        when(uclVerifications.findLatestByLoanApplicationId(f.application.id())).thenReturn(Optional.empty());
        QueryContractReadinessUseCase.Snapshot blocked = service.query(f.application.id(), 1);
        assertFalse(blocked.ready());
        assertTrue(blocked.blockers().contains(ContractReadinessBlockerCode.UCL_VERIFICATION_INVALID));
    }

    @Test void collateralReadinessRequiresVerifiedEvidenceWithoutSalaryReservationAccess() {
        CollateralFixture f = collateralFixture();
        LoanContract acknowledged = collateralContract(f, LoanContractStatus.ACKNOWLEDGED);
        when(users.currentUser()).thenReturn(staff());
        when(applications.findById(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationId(f.application.id()))
                .thenReturn(Optional.of(acknowledged));
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(corrections.existsActiveRequestByApplicationId(f.application.id())).thenReturn(false);
        when(bankAccounts.inspectCaptured(f.application.customerId(), f.accountId))
                .thenReturn(new ContractBankAccountPort.ContractBankAccountState(true, true, true));
        when(collateralVerifications.findLatestByLoanApplicationId(f.application.id()))
                .thenReturn(Optional.of(f.verification));

        QueryContractReadinessUseCase.Snapshot ready = service.query(f.application.id(), 1);

        assertTrue(ready.ready());
        assertTrue(ready.blockers().isEmpty());
        verifyNoInteractions(verifications, uclVerifications, limits, movements);

        reset(collateralVerifications);
        when(collateralVerifications.findLatestByLoanApplicationId(f.application.id()))
                .thenReturn(Optional.empty());
        QueryContractReadinessUseCase.Snapshot blocked = service.query(f.application.id(), 1);
        assertFalse(blocked.ready());
        assertTrue(blocked.blockers().contains(
                ContractReadinessBlockerCode.COLLATERAL_VERIFICATION_INVALID
        ));
    }

    @Test void activeCorrectionBlocksPreparationBeforeCustomerOrReservationChecks() {
        Fixture f = fixture(); when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id()))
                .thenReturn(Optional.of(mock(LoanCorrectionRequest.class)));
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals("ACTIVE_CORRECTION_REQUEST", error.getErrorCode());
        verifyNoInteractions(bankAccounts);
    }

    @Test void inactiveCustomerFromNarrowBankBoundaryBlocksPreparation() {
        Fixture f = fixture(); when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(bankAccounts.capturePrimaryActive(f.application.customerId())).thenThrow(
                new BusinessStateConflictException("CUSTOMER_INACTIVE", "Customer is not active."));
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals("CUSTOMER_INACTIVE", error.getErrorCode());
    }

    @Test void invalidReservationBlocksPreparationWithoutPersistingContract() {
        Fixture f = fixture(); stubPreparation(f);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVED)).thenReturn(List.of());
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals("SALARY_ADVANCE_RESERVATION_INVALID", error.getErrorCode());
        verify(contracts, never()).save(any());
    }

    @Test void reservationAmountMustExactlyEqualContractPrincipal() {
        for (long amount : List.of(999L, 1001L)) {
            Fixture f = fixture();
            stubPreparation(f);
            when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                    f.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                    .thenReturn(List.of(reservation(f, f.limit.id(), money(amount))));
            assertPreparationBlocked(f, "SALARY_ADVANCE_RESERVATION_INVALID");
        }
        verify(contracts, never()).save(any());
    }

    @Test void reservationMustReferenceVerificationLimitAndBeUnique() {
        Fixture wrongLimit = fixture();
        stubPreparation(wrongLimit);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                wrongLimit.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(reservation(wrongLimit, UUID.randomUUID(), money(1000))));
        assertPreparationBlocked(wrongLimit, "SALARY_ADVANCE_RESERVATION_INVALID");

        Fixture duplicate = fixture();
        stubPreparation(duplicate);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                duplicate.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(
                        reservation(duplicate, duplicate.limit.id(), money(1000)),
                        reservation(duplicate, duplicate.limit.id(), money(1000))));
        assertPreparationBlocked(duplicate, "SALARY_ADVANCE_RESERVATION_INVALID");
        verify(contracts, never()).save(any());
    }

    @Test void limitReservedBalanceMustReconcileWithOutstandingMovements() {
        Fixture f = fixture();
        stubPreparation(f);
        when(movements.calculateOutstandingReservedAmount(f.limit.id())).thenReturn(money(999));
        assertPreparationBlocked(f, "SALARY_ADVANCE_RESERVATION_INVALID");
        verify(contracts, never()).save(any());
    }

    @Test void releasedReservationBlocksPreparation() {
        Fixture f = fixture();
        stubPreparation(f);
        when(movements.existsByLoanApplicationIdAndMovementType(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED)).thenReturn(true);
        assertPreparationBlocked(f, "SALARY_ADVANCE_RESERVATION_RELEASED");
        verify(contracts, never()).save(any());
    }

    @Test void confirmationUsesImmutableContractPrincipalInsteadOfCurrentOfferData() {
        Fixture f = fixture();
        LoanContract acknowledged = contract(f, 1, LoanContractStatus.ACKNOWLEDGED);
        ApprovedOffer changedOffer = changedOffer(f, money(2000));
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByConfirmationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(changedOffer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(acknowledged));
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(bankAccounts.inspectCapturedForUpdate(f.application.customerId(), f.accountId))
                .thenReturn(new ContractBankAccountPort.ContractBankAccountState(true, true, true));
        when(verifications.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.verification));
        when(limits.findByIdForUpdate(f.limit.id())).thenReturn(Optional.of(f.limit));
        when(movements.existsByLoanApplicationIdAndMovementType(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED)).thenReturn(false);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(reservation(f, f.limit.id(), money(1000))));
        when(movements.calculateOutstandingReservedAmount(f.limit.id())).thenReturn(money(1000));
        when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanContract ready = service.confirm(new ConfirmContractReadinessUseCase.Command(
                UUID.randomUUID(), f.application.id(), acknowledged.id(), 1));

        assertEquals(LoanContractStatus.READY_FOR_DISBURSEMENT, ready.status());
    }

    @Test void preparationPassesThroughUnrelatedConstraintFailures() {
        Fixture f = fixture();
        stubPreparation(f);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
        when(contracts.save(any())).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), f.application.id(), 0, null)));

        assertSame(failure, thrown);
    }

    @Test void acknowledgmentPassesThroughUnrelatedConstraintFailures() {
        Fixture f = fixture();
        LoanContract prepared = contract(f, 1, LoanContractStatus.PREPARED);
        AuthenticatedUser owner = new AuthenticatedUser(
                UUID.randomUUID(), "owner@meridian.test", "CUSTOMER", f.application.customerId(),
                Set.of("CUSTOMER"), Set.of());
        when(users.currentUser()).thenReturn(owner);
        when(contracts.findByAcknowledgmentRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(prepared));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
        when(contracts.save(any())).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        UUID.randomUUID(), f.application.id(), 1)));

        assertSame(failure, thrown);
    }

    @Test void confirmationPassesThroughUnrelatedConstraintFailures() {
        Fixture f = fixture();
        LoanContract acknowledged = contract(f, 1, LoanContractStatus.ACKNOWLEDGED);
        stubConfirmation(f, acknowledged, f.offer);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
        when(contracts.save(any())).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.confirm(new ConfirmContractReadinessUseCase.Command(
                        UUID.randomUUID(), f.application.id(), acknowledged.id(), 1)));

        assertSame(failure, thrown);
    }

    private void assertPreparationBlocked(Fixture f, String expectedCode) {
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals(expectedCode, error.getErrorCode());
    }
    private void stubPreparation(Fixture f) {
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(bankAccounts.capturePrimaryActive(f.application.customerId())).thenReturn(f.sensitive);
        when(protector.protect(any(byte[].class), any())).thenReturn(
                new ProtectedBankAccountEnvelope("AES-256-GCM", "v1", new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1"));
        when(verifications.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.verification));
        when(limits.findByIdForUpdate(f.limit.id())).thenReturn(Optional.of(f.limit));
        when(movements.existsByLoanApplicationIdAndMovementType(f.application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED)).thenReturn(false);
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(reservation(f, f.limit.id(), money(1000))));
        lenient().when(movements.calculateOutstandingReservedAmount(f.limit.id())).thenReturn(money(1000));
    }

    private void stubConfirmation(Fixture f, LoanContract acknowledged, ApprovedOffer offer) {
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByConfirmationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(acknowledged));
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(bankAccounts.inspectCapturedForUpdate(f.application.customerId(), f.accountId))
                .thenReturn(new ContractBankAccountPort.ContractBankAccountState(true, true, true));
        when(verifications.findByLoanApplicationId(f.application.id())).thenReturn(Optional.of(f.verification));
        when(limits.findByIdForUpdate(f.limit.id())).thenReturn(Optional.of(f.limit));
        when(movements.existsByLoanApplicationIdAndMovementType(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED)).thenReturn(false);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(reservation(f, f.limit.id(), money(1000))));
        when(movements.calculateOutstandingReservedAmount(f.limit.id())).thenReturn(money(1000));
    }

    private void stubCollateralPreparation(CollateralFixture f) {
        stubCollateralPreparationWithoutVerification(f);
        when(bankAccounts.capturePrimaryActive(f.application.customerId())).thenReturn(f.sensitive);
        when(protector.protect(any(byte[].class), any())).thenReturn(
                new ProtectedBankAccountEnvelope(
                        "AES-256-GCM", "v1", new byte[12], new byte[]{1},
                        "DISBURSEMENT_ACCOUNT_V1"
                )
        );
    }

    private void stubCollateralPreparationWithoutVerification(CollateralFixture f) {
        when(users.currentUser()).thenReturn(staff());
        when(contracts.findByPreparationRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        when(offers.findByLoanApplicationIdForUpdate(f.application.id())).thenReturn(Optional.of(f.offer));
        when(contracts.findCurrentByApplicationIdForUpdate(f.application.id())).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(f.application.id()))
                .thenReturn(Optional.empty());
        when(documents.isProcessingReady(f.application.id())).thenReturn(true);
    }

    private static AuthenticatedUser staff() {
        return new AuthenticatedUser(UUID.randomUUID(), "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of());
    }
    private static Fixture fixture() {
        UUID customerId = UUID.randomUUID(), applicationId = UUID.randomUUID(), linkId = UUID.randomUUID(), limitId = UUID.randomUUID();
        LoanApplication application = new LoanApplication(applicationId, customerId, UUID.randomUUID(), "SA-1",
                ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED, LoanApplicationStatus.CONTRACT_PENDING,
                money(1000), 1, java.time.LocalDateTime.now());
        ApprovedOfferFinancialTerms terms = terms();
        ApprovedOffer offer = new ApprovedOffer(UUID.randomUUID(), applicationId, UUID.randomUUID(), ApprovedOfferStatus.ACCEPTED,
                terms, List.of(new ProvisionalRepaymentItem(UUID.randomUUID(), 1, money(1000), money(100), money(0), money(1100))),
                java.time.LocalDateTime.now().minusDays(2), java.time.LocalDateTime.now().minusDays(1), java.time.LocalDateTime.now(), null, null);
        SalaryAdvanceLimit limit = new SalaryAdvanceLimit(limitId, customerId, linkId, money(2000), money(0), money(1000), money(1000),
                SalaryAdvanceLimitStatus.ACTIVE, java.time.LocalDateTime.now());
        SalaryAdvanceVerification verification = new SalaryAdvanceVerification(UUID.randomUUID(), applicationId, customerId, linkId,
                limitId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                ProductVerificationResult.VERIFIED, money(2000), money(0), money(1000), money(1000), java.time.LocalDateTime.now());
        UUID accountId = UUID.randomUUID();
        SensitiveDisbursementBankAccountDetails sensitive = new SensitiveDisbursementBankAccountDetails(customerId, accountId,
                "VCB", "Vietcombank", "MERIDIAN CUSTOMER", "7890", new byte[]{1,2,3,4,5,6});
        return new Fixture(application, offer, limit, verification, accountId, sensitive);
    }
    private static ApprovedOffer changedOffer(Fixture f, BigDecimal principal) {
        ApprovedOfferFinancialTerms changedTerms = new ApprovedOfferFinancialTerms(principal, 1,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL, new BigDecimal("0.100000"),
                money(100), money(0), principal.add(money(100)), RepaymentMethod.ON_SALARY_DATE);
        return new ApprovedOffer(f.offer.id(), f.application.id(), f.offer.sourceLoanProductPolicyId(),
                ApprovedOfferStatus.ACCEPTED, changedTerms,
                List.of(new ProvisionalRepaymentItem(UUID.randomUUID(), 1,
                        principal, money(100), money(0), principal.add(money(100)))),
                f.offer.generatedAt(), f.offer.expiresAt(), f.offer.acceptedAt(), null, null);
    }

    private static SalaryAdvanceLimitMovement reservation(Fixture f, UUID limitId, BigDecimal amount) {
        return SalaryAdvanceLimitMovement.reserved(
                UUID.randomUUID(), limitId, f.application.id(), amount, java.time.LocalDateTime.now());
    }
    private static LoanContract contract(Fixture f, int version, LoanContractStatus status) {
        java.time.LocalDateTime preparedAt = java.time.LocalDateTime.of(2026, 7, 22, 23, 0);
        LoanContract prepared = LoanContract.prepared(UUID.randomUUID(), f.application.id(), f.offer.id(), "MCT-" + version, version,
                f.offer.financialTerms(), List.of(new LoanContractRepaymentItem(UUID.randomUUID(), f.offer.repaymentItems().getFirst().id(),
                        1, money(1000), money(100), money(0), money(1100))),
                new ProtectedDisbursementBankAccount(f.application.customerId(), f.accountId, "VCB", "Vietcombank", "MERIDIAN CUSTOMER",
                        "7890", true, true, preparedAt, "AES-256-GCM", "v1", new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1"),
                UUID.randomUUID(), version == 1 ? null : version - 1, version == 1 ? null : ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH,
                UUID.randomUUID(), preparedAt, version == 1 ? null : UUID.randomUUID());
        return status == LoanContractStatus.ACKNOWLEDGED
                ? prepared.acknowledge(UUID.randomUUID(), UUID.randomUUID(), preparedAt.plusMinutes(1)) : prepared;
    }
    private static ApprovedOfferFinancialTerms terms() { return new ApprovedOfferFinancialTerms(money(1000), 1,
            InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL, new BigDecimal("0.100000"), money(100), money(0), money(1100), RepaymentMethod.ON_SALARY_DATE); }
    private static UclFixture uclFixture() {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 7, 22, 23, 0);
        LoanApplication application = new LoanApplication(
                applicationId, customerId, UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.CONTRACT_PENDING, money(3_000_000), 3, now.minusDays(5)
        );
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(3_000_000), 3, InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.018000"), money(162_000), money(0), money(3_162_000),
                RepaymentMethod.MONTHLY_INSTALLMENT
        );
        List<ProvisionalRepaymentItem> items = java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(number -> new ProvisionalRepaymentItem(
                        UUID.randomUUID(), number, money(1_000_000), money(54_000),
                        money(0), money(1_054_000)
                ))
                .toList();
        ApprovedOffer offer = new ApprovedOffer(
                UUID.randomUUID(), applicationId, UUID.randomUUID(), ApprovedOfferStatus.ACCEPTED,
                terms, items, now.minusDays(2), now.plusDays(5), now.minusDays(1), null, null
        );
        UnsecuredConsumerLoanVerification verification = new UnsecuredConsumerLoanVerification(
                UUID.randomUUID(), applicationId, ProductVerificationResult.VERIFIED,
                now.minusDays(4), UUID.randomUUID(), now.minusDays(3),
                "Verified UCL evidence."
        );
        SensitiveDisbursementBankAccountDetails sensitive =
                new SensitiveDisbursementBankAccountDetails(
                        customerId, accountId, "VCB", "Vietcombank", "MERIDIAN CUSTOMER",
                        "7890", new byte[]{1, 2, 3, 4, 5, 6}
                );
        return new UclFixture(application, offer, verification, accountId, sensitive);
    }
    private static LoanContract uclContract(UclFixture f, LoanContractStatus status) {
        java.time.LocalDateTime preparedAt = java.time.LocalDateTime.of(2026, 7, 22, 23, 0);
        List<LoanContractRepaymentItem> items = f.offer.repaymentItems().stream()
                .map(item -> new LoanContractRepaymentItem(
                        UUID.randomUUID(), item.id(), item.installmentNumber(), item.principalDue(),
                        item.interestDue(), item.feeDue(), item.totalDue()
                ))
                .toList();
        LoanContract prepared = LoanContract.prepared(
                UUID.randomUUID(), f.application.id(), f.offer.id(), "MCT-UCL-1", 1,
                f.offer.financialTerms(), items,
                new ProtectedDisbursementBankAccount(
                        f.application.customerId(), f.accountId, "VCB", "Vietcombank",
                        "MERIDIAN CUSTOMER", "7890", true, true, preparedAt,
                        "AES-256-GCM", "v1", new byte[12], new byte[]{1},
                        "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.randomUUID(), null, null, UUID.randomUUID(), preparedAt, null
        );
        return status == LoanContractStatus.ACKNOWLEDGED
                ? prepared.acknowledge(UUID.randomUUID(), UUID.randomUUID(), preparedAt.plusMinutes(1))
                : prepared;
    }

    private static CollateralFixture collateralFixture() {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 8, 19, 9, 0);
        LoanApplication application = new LoanApplication(
                applicationId, customerId, UUID.randomUUID(), "COLLATERAL-CONTRACT-1",
                ProductCode.COLLATERAL_LOAN, ProductType.SECURED,
                LoanApplicationStatus.CONTRACT_PENDING, money(25_000_000), 6,
                now.minusDays(10)
        );
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(25_000_000), 6, InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"), money(2_250_000), money(0), money(27_250_000),
                RepaymentMethod.MONTHLY_INSTALLMENT
        );
        List<ProvisionalRepaymentItem> items = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(number -> new ProvisionalRepaymentItem(
                        UUID.randomUUID(), number,
                        number == 6 ? money(4_166_665) : money(4_166_667),
                        money(375_000), money(0),
                        number == 6 ? money(4_541_665) : money(4_541_667)
                ))
                .toList();
        ApprovedOffer offer = new ApprovedOffer(
                UUID.randomUUID(), applicationId, UUID.randomUUID(), ApprovedOfferStatus.ACCEPTED,
                terms, items, now.minusDays(3), now.plusDays(4), now.minusDays(1), null, null
        );
        CollateralLoanVerification verification = collateralVerification(
                applicationId, ProductVerificationResult.VERIFIED
        );
        SensitiveDisbursementBankAccountDetails sensitive =
                new SensitiveDisbursementBankAccountDetails(
                        customerId, accountId, "TEST", "Test Bank", "COLLATERAL CUSTOMER",
                        "5678", new byte[]{1, 2, 3, 4, 5, 6}
                );
        return new CollateralFixture(
                application, offer, verification, accountId, sensitive
        );
    }

    private static CollateralLoanVerification collateralVerification(
            UUID applicationId,
            ProductVerificationResult result
    ) {
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.of(2026, 8, 12, 9, 0);
        if (result == ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            return new CollateralLoanVerification(
                    UUID.randomUUID(), applicationId, result, createdAt
            );
        }
        return new CollateralLoanVerification(
                UUID.randomUUID(), applicationId, 1, null, result, createdAt,
                UUID.randomUUID(), createdAt.plusHours(1), "Reviewed Collateral evidence."
        );
    }

    private static LoanContract collateralContract(
            CollateralFixture f,
            LoanContractStatus status
    ) {
        java.time.LocalDateTime preparedAt = java.time.LocalDateTime.of(2026, 8, 18, 9, 0);
        List<LoanContractRepaymentItem> items = f.offer.repaymentItems().stream()
                .map(item -> new LoanContractRepaymentItem(
                        UUID.randomUUID(), item.id(), item.installmentNumber(),
                        item.principalDue(), item.interestDue(), item.feeDue(), item.totalDue()
                ))
                .toList();
        LoanContract prepared = LoanContract.prepared(
                UUID.randomUUID(), f.application.id(), f.offer.id(), "MCT-COLLATERAL-1", 1,
                f.offer.financialTerms(), items,
                new ProtectedDisbursementBankAccount(
                        f.application.customerId(), f.accountId, "TEST", "Test Bank",
                        "COLLATERAL CUSTOMER", "5678", true, true, preparedAt,
                        "AES-256-GCM", "v1", new byte[12], new byte[]{1},
                        "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.randomUUID(), null, null, UUID.randomUUID(), preparedAt, null
        );
        return status == LoanContractStatus.ACKNOWLEDGED
                ? prepared.acknowledge(
                        UUID.randomUUID(), UUID.randomUUID(), preparedAt.plusMinutes(1)
                )
                : prepared;
    }
    private static BigDecimal money(long amount) { return BigDecimal.valueOf(amount).setScale(2); }
    private record Fixture(LoanApplication application, ApprovedOffer offer, SalaryAdvanceLimit limit,
                           SalaryAdvanceVerification verification, UUID accountId,
                           SensitiveDisbursementBankAccountDetails sensitive) {}
    private record UclFixture(
            LoanApplication application,
            ApprovedOffer offer,
            UnsecuredConsumerLoanVerification verification,
            UUID accountId,
            SensitiveDisbursementBankAccountDetails sensitive
    ) {}
    private record CollateralFixture(
            LoanApplication application,
            ApprovedOffer offer,
            CollateralLoanVerification verification,
            UUID accountId,
            SensitiveDisbursementBankAccountDetails sensitive
    ) {}
}

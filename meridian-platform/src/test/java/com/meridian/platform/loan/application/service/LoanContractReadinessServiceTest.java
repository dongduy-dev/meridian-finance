package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.*;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
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
    @Mock SalaryAdvanceLimitRepository limits;
    @Mock SalaryAdvanceLimitMovementRepository movements;
    @Mock LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock BusinessAuditPublisher audit;
    @Mock CurrentUserProvider users;
    LoanContractReadinessService service;

    @BeforeEach void setUp() {
        service = new LoanContractReadinessService(applications, offers, contracts, corrections, documents,
                bankAccounts, protector, verifications, limits, movements, transitionRecorder, audit, users,
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
    private static BigDecimal money(long amount) { return BigDecimal.valueOf(amount).setScale(2); }
    private record Fixture(LoanApplication application, ApprovedOffer offer, SalaryAdvanceLimit limit,
                           SalaryAdvanceVerification verification, UUID accountId,
                           SensitiveDisbursementBankAccountDetails sensitive) {}
}

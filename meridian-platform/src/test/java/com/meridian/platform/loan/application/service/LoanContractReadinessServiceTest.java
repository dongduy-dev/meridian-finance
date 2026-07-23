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

    @Test void acknowledgmentRequiresAuthenticatedCustomerOwnership() {
        Fixture f = fixture(); LoanContract current = contract(f, 1, LoanContractStatus.PREPARED);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "c@meridian.test", "CUSTOMER",
                UUID.randomUUID(), Set.of("CUSTOMER"), Set.of()));
        when(contracts.findByAcknowledgmentRequestId(any())).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(f.application.id())).thenReturn(Optional.of(f.application));
        assertThrows(AuthorizationException.class, () -> service.acknowledge(
                new AcknowledgeLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), current.id(), 1)));
        verify(contracts, never()).save(any());
    }

    @Test void readinessReportsInactiveCapturedAccountAndReleasedReservation() {
        Fixture f = fixture(); LoanContract acknowledged = contract(f, 1, LoanContractStatus.ACKNOWLEDGED);
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
        QueryContractReadinessUseCase.Snapshot snapshot = service.query(f.application.id(), 1);
        assertTrue(snapshot.blockers().contains(ContractReadinessBlockerCode.CAPTURED_ACCOUNT_INACTIVE));
        assertTrue(snapshot.blockers().contains(ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_RELEASED));
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
        when(movements.existsByLoanApplicationIdAndMovementType(
                f.application.id(), SalaryAdvanceLimitMovementType.RESERVED)).thenReturn(false);
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.application.id(), 0, null)));
        assertEquals("SALARY_ADVANCE_RESERVATION_INVALID", error.getErrorCode());
        verify(contracts, never()).save(any());
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
        when(movements.existsByLoanApplicationIdAndMovementType(f.application.id(), SalaryAdvanceLimitMovementType.RESERVED)).thenReturn(true);
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
    private static LoanContract contract(Fixture f, int version, LoanContractStatus status) {
        LoanContract prepared = LoanContract.prepared(UUID.randomUUID(), f.application.id(), f.offer.id(), "MCT-" + version, version,
                f.offer.financialTerms(), List.of(new LoanContractRepaymentItem(UUID.randomUUID(), f.offer.repaymentItems().getFirst().id(),
                        1, money(1000), money(100), money(0), money(1100))),
                new ProtectedDisbursementBankAccount(f.application.customerId(), f.accountId, "VCB", "Vietcombank", "MERIDIAN CUSTOMER",
                        "7890", true, true, java.time.LocalDateTime.now(), "AES-256-GCM", "v1", new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1"),
                UUID.randomUUID(), version == 1 ? null : version - 1, version == 1 ? null : ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH,
                UUID.randomUUID(), java.time.LocalDateTime.now(), version == 1 ? null : UUID.randomUUID());
        return status == LoanContractStatus.ACKNOWLEDGED
                ? prepared.acknowledge(UUID.randomUUID(), UUID.randomUUID(), java.time.LocalDateTime.now()) : prepared;
    }
    private static ApprovedOfferFinancialTerms terms() { return new ApprovedOfferFinancialTerms(money(1000), 1,
            InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL, new BigDecimal("0.100000"), money(100), money(0), money(1100), RepaymentMethod.ON_SALARY_DATE); }
    private static BigDecimal money(long amount) { return BigDecimal.valueOf(amount).setScale(2); }
    private record Fixture(LoanApplication application, ApprovedOffer offer, SalaryAdvanceLimit limit,
                           SalaryAdvanceVerification verification, UUID accountId,
                           SensitiveDisbursementBankAccountDetails sensitive) {}
}

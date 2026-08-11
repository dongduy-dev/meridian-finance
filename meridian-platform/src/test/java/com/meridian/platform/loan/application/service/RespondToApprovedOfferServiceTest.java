package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanOfferPolicy;
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.loan.domain.service.UnsecuredConsumerLoanOfferCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.model.ActorType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespondToApprovedOfferServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LIMIT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private FakeApprovedOfferRepository approvedOfferRepository;
    private FakeSalaryAdvanceLimitRepository limitRepository;
    private FakeSalaryAdvanceLimitMovementRepository movementRepository;
    private FakeLoanApplicationStatusTransitionRepository transitionRepository;
    private FakeBusinessAuditPublisher auditPublisher;
    private RespondToApprovedOfferService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        approvedOfferRepository = new FakeApprovedOfferRepository();
        FakeSalaryAdvanceVerificationRepository verificationRepository = new FakeSalaryAdvanceVerificationRepository();
        limitRepository = new FakeSalaryAdvanceLimitRepository();
        movementRepository = new FakeSalaryAdvanceLimitMovementRepository();
        transitionRepository = new FakeLoanApplicationStatusTransitionRepository();
        auditPublisher = new FakeBusinessAuditPublisher();
        SalaryAdvanceReservationReleaseService releaseService = new SalaryAdvanceReservationReleaseService(
                verificationRepository,
                limitRepository,
                movementRepository,
                auditPublisher
        );
        service = new RespondToApprovedOfferService(
                loanApplicationRepository,
                approvedOfferRepository,
                new FixedCurrentUserProvider(CUSTOMER_ID),
                releaseService,
                new LoanApplicationStatusTransitionRecorder(transitionRepository),
                auditPublisher,
                new ApprovedOfferMapper(),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsPendingOffer() {
        ApprovedOfferActionResult result = service.acceptOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("ACCEPTED", result.offer().status());
        assertEquals(ApprovedOfferStatus.ACCEPTED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.CONTRACT_PENDING, loanApplicationRepository.savedApplication.status());
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertEquals(1, transitionRepository.savedTransitions.size());
        assertEquals(ActorType.USER, transitionRepository.savedTransitions.getFirst().actorType());
        assertEquals(BusinessAuditAction.APPROVED_OFFER_ACCEPTED, auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void acceptsPendingUclOfferWithoutSalaryExposureEffects() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        approvedOfferRepository.offer = pendingUclOffer(NOW.plusDays(3));

        ApprovedOfferActionResult result = service.acceptOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("ACCEPTED", result.offer().status());
        assertEquals(LoanApplicationStatus.CONTRACT_PENDING, loanApplicationRepository.savedApplication.status());
        assertNull(limitRepository.savedLimit);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void acceptAfterAcceptReturnsCurrentAcceptedResult() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.CONTRACT_PENDING, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.plusDays(3)).accept(NOW.minusHours(1));

        ApprovedOfferActionResult result = service.acceptOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("ACCEPTED", result.offer().status());
        assertNoOfferResponseEffects();
    }

    @Test
    void declinesPendingOfferAndReleasesReservation() {
        ApprovedOfferActionResult result = service.declineOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("DECLINED", result.offer().status());
        assertEquals(ApprovedOfferStatus.DECLINED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.CUSTOMER_DECLINED, loanApplicationRepository.savedApplication.status());
        assertEquals(1, movementRepository.savedMovements.size());
        assertEquals(SalaryAdvanceLimitMovementType.RESERVATION_RELEASED,
                movementRepository.savedMovements.get(0).movementType());
        assertEquals(money(0), limitRepository.savedLimit.reservedAmount());
        assertEquals(1, transitionRepository.savedTransitions.size());
        assertEquals(ActorType.USER, transitionRepository.savedTransitions.getFirst().actorType());
        assertEquals(BusinessAuditAction.RESERVATION_RELEASED, auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void declinesPendingUclOfferWithoutSalaryExposureEffects() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        approvedOfferRepository.offer = pendingUclOffer(NOW.plusDays(3));

        ApprovedOfferActionResult result = service.declineOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("DECLINED", result.offer().status());
        assertEquals(LoanApplicationStatus.CUSTOMER_DECLINED, loanApplicationRepository.savedApplication.status());
        assertNull(limitRepository.savedLimit);
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertEquals(BusinessAuditAction.APPROVED_OFFER_DECLINED,
                auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void declineAfterDeclineReturnsCurrentDeclinedResult() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.CUSTOMER_DECLINED, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.plusDays(3)).decline(NOW.minusHours(1));

        ApprovedOfferActionResult result = service.declineOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, result.outcome());
        assertEquals("DECLINED", result.offer().status());
        assertNoOfferResponseEffects();
    }

    @Test
    void expiredAcceptCommitsExpiryAndReturnsExpiredOutcome() {
        approvedOfferRepository.offer = pendingOffer(NOW);

        ApprovedOfferActionResult result = service.acceptOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.EXPIRED, result.outcome());
        assertEquals("EXPIRED", result.offer().status());
        assertEquals(ApprovedOfferStatus.EXPIRED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.EXPIRED, loanApplicationRepository.savedApplication.status());
        assertEquals(1, movementRepository.savedMovements.size());
    }

    @Test
    void expiredDeclineCommitsExpiryAndReturnsExpiredOutcome() {
        approvedOfferRepository.offer = pendingOffer(NOW.minusSeconds(1));

        ApprovedOfferActionResult result = service.declineOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.EXPIRED, result.outcome());
        assertEquals("EXPIRED", result.offer().status());
        assertEquals(ApprovedOfferStatus.EXPIRED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.EXPIRED, loanApplicationRepository.savedApplication.status());
        assertEquals(1, movementRepository.savedMovements.size());
    }

    @Test
    void acceptPersistedExpiredOfferReturnsExpiredWithoutNewEffects() {
        LocalDateTime persistedExpiredAt = NOW.minusHours(2);
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.EXPIRED, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.minusHours(3)).expire(persistedExpiredAt);

        ApprovedOfferActionResult result = service.acceptOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.EXPIRED, result.outcome());
        assertEquals("EXPIRED", result.offer().status());
        assertEquals(persistedExpiredAt, result.offer().expiredAt());
        assertNoOfferResponseEffects();
    }

    @Test
    void declinePersistedExpiredOfferReturnsExpiredWithoutNewEffects() {
        LocalDateTime persistedExpiredAt = NOW.minusHours(2);
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.EXPIRED, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.minusHours(3)).expire(persistedExpiredAt);

        ApprovedOfferActionResult result = service.declineOffer(LOAN_APPLICATION_ID);

        assertEquals(ApprovedOfferActionOutcome.EXPIRED, result.outcome());
        assertEquals("EXPIRED", result.offer().status());
        assertEquals(persistedExpiredAt, result.offer().expiredAt());
        assertNoOfferResponseEffects();
    }

    @Test
    void acceptAfterDeclineConflicts() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.CUSTOMER_DECLINED, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.plusDays(3)).decline(NOW.minusHours(1));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.acceptOffer(LOAN_APPLICATION_ID)
        );

        assertEquals("OFFER_ACTION_CONFLICT", exception.getErrorCode());
        assertNoOfferResponseEffects();
    }

    @Test
    void declineAfterAcceptConflicts() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.CONTRACT_PENDING, CUSTOMER_ID);
        approvedOfferRepository.offer = pendingOffer(NOW.plusDays(3)).accept(NOW.minusHours(1));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.declineOffer(LOAN_APPLICATION_ID)
        );

        assertEquals("OFFER_ACTION_CONFLICT", exception.getErrorCode());
        assertNoOfferResponseEffects();
    }

    @Test
    void enforcesCustomerOwnershipThroughLoanApplication() {
        loanApplicationRepository.application = loanApplication(
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                OTHER_CUSTOMER_ID
        );

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> service.acceptOffer(LOAN_APPLICATION_ID)
        );

        assertEquals("ACCESS_DENIED", exception.getErrorCode());
        assertNull(approvedOfferRepository.savedOffer);
    }

    private void assertNoOfferResponseEffects() {
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
        assertNull(limitRepository.savedLimit);
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertTrue(transitionRepository.savedTransitions.isEmpty());
        assertTrue(auditPublisher.publishedEvents.isEmpty());
    }

    private ApprovedOffer pendingOffer(LocalDateTime expiresAt) {
        ApprovedOffer generated = new SalaryAdvanceOfferCalculator().generate(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                LOAN_APPLICATION_ID,
                new SalaryAdvanceOfferPolicy(
                        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                        InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                        new BigDecimal("0.012000"),
                        money(0),
                        RepaymentMethod.ON_SALARY_DATE,
                        7,
                        Set.of(1, 2, 3)
                ),
                money(3_000_000),
                1,
                NOW.minusDays(1)
        );
        return new ApprovedOffer(
                generated.id(),
                generated.loanApplicationId(),
                generated.sourceLoanProductPolicyId(),
                generated.status(),
                generated.financialTerms(),
                generated.repaymentItems(),
                generated.generatedAt(),
                expiresAt,
                generated.acceptedAt(),
                generated.declinedAt(),
                generated.expiredAt()
        );
    }

    private ApprovedOffer pendingUclOffer(LocalDateTime expiresAt) {
        ApprovedOffer generated = new UnsecuredConsumerLoanOfferCalculator().generate(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                LOAN_APPLICATION_ID,
                new UnsecuredConsumerLoanOfferPolicy(
                        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                        InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                        new BigDecimal("0.018000"),
                        money(0),
                        RepaymentMethod.MONTHLY_INSTALLMENT,
                        7,
                        Set.of(3, 6, 9, 12)
                ),
                money(5_000_000),
                6,
                NOW.minusDays(1)
        );
        return new ApprovedOffer(
                generated.id(), generated.loanApplicationId(), generated.sourceLoanProductPolicyId(),
                generated.status(), generated.financialTerms(), generated.repaymentItems(),
                generated.generatedAt(), expiresAt, null, null, null
        );
    }

    private LoanApplication loanApplication(LoanApplicationStatus status, UUID customerId) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                customerId,
                UUID.fromString("12121212-1212-1212-1212-121212121212"),
                "SA-20260706-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                money(3_000_000),
                1,
                NOW.minusDays(2)
        );
    }

    private LoanApplication uclApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                CUSTOMER_ID,
                UUID.fromString("13131313-1313-1313-1313-131313131313"),
                "UCL-20260706-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                money(5_000_000),
                6,
                NOW.minusDays(2)
        );
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        private final UUID customerId;

        private FixedCurrentUserProvider(UUID customerId) {
            this.customerId = customerId;
        }

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000301"),
                    "customer.demo@meridian.local",
                    "CUSTOMER",
                    customerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:read:own", "loan:offer:respond:own")
            );
        }
    }

    private class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        }

        private LoanApplication application = loanApplication(
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                CUSTOMER_ID
        );
        private LoanApplication savedApplication;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplication = loanApplication;
            application = loanApplication;
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findById(UUID loanApplicationId) {
            return Optional.ofNullable(application)
                    .filter(value -> value.id().equals(loanApplicationId));
        }

        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return findById(loanApplicationId);
        }

        @Override
        public boolean existsByCustomerIdAndProductCodeAndStatusIn(
                UUID customerId,
                ProductCode productCode,
                Set<LoanApplicationStatus> statuses
        ) {
            return false;
        }

        @Override
        public long nextApplicationNumberSequence() {
            return 1L;
        }
    }

    private class FakeApprovedOfferRepository implements ApprovedOfferRepository {

        private ApprovedOffer offer = pendingOffer(NOW.plusDays(3));
        private ApprovedOffer savedOffer;

        @Override
        public ApprovedOffer save(ApprovedOffer approvedOffer) {
            savedOffer = approvedOffer;
            offer = approvedOffer;
            return approvedOffer;
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId) {
            return Optional.ofNullable(offer)
                    .filter(value -> value.loanApplicationId().equals(loanApplicationId));
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return findByLoanApplicationId(loanApplicationId);
        }

        @Override
        public List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize) {
            return List.of();
        }
    }

    private class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            return salaryAdvanceVerification;
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
            if (!LOAN_APPLICATION_ID.equals(loanApplicationId)) {
                return Optional.empty();
            }
            return Optional.of(new SalaryAdvanceVerification(
                    UUID.randomUUID(),
                    LOAN_APPLICATION_ID,
                    CUSTOMER_ID,
                    LINK_ID,
                    LIMIT_ID,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                    ProductVerificationResult.VERIFIED,
                    money(6_000_000),
                    money(0),
                    money(3_000_000),
                    money(3_000_000),
                    NOW.minusDays(2)
            ));
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return findByLoanApplicationId(loanApplicationId);
        }
    }

    private class FakeSalaryAdvanceLimitRepository implements SalaryAdvanceLimitRepository {

        private SalaryAdvanceLimit currentLimit = new SalaryAdvanceLimit(
                LIMIT_ID,
                CUSTOMER_ID,
                LINK_ID,
                money(6_000_000),
                money(0),
                money(3_000_000),
                money(3_000_000),
                SalaryAdvanceLimitStatus.ACTIVE,
                NOW.minusDays(2)
        );
        private SalaryAdvanceLimit savedLimit;

        @Override
        public void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId) {
        }

        @Override
        public Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            return Optional.of(currentLimit);
        }

        @Override
        public SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit) {
            savedLimit = salaryAdvanceLimit;
            currentLimit = salaryAdvanceLimit;
            return salaryAdvanceLimit;
        }
    }


    private static class FakeLoanApplicationStatusTransitionRepository implements LoanApplicationStatusTransitionRepository {

        private final List<LoanApplicationStatusTransition> savedTransitions = new ArrayList<>();

        @Override
        public int nextSequenceNumber(UUID loanApplicationId) {
            return savedTransitions.size() + 1;
        }

        @Override
        public LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition) {
            savedTransitions.add(transition);
            return transition;
        }
    }

    private static class FakeBusinessAuditPublisher implements BusinessAuditPublisher {

        private final List<BusinessAuditEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publish(BusinessAuditEvent event) {
            publishedEvents.add(event);
        }

        private BusinessAuditEvent lastEvent() {
            return publishedEvents.getLast();
        }
    }
    private static class FakeSalaryAdvanceLimitMovementRepository implements SalaryAdvanceLimitMovementRepository {

        private final List<SalaryAdvanceLimitMovement> savedMovements = new ArrayList<>();

        @Override
        public SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement) {
            savedMovements.add(salaryAdvanceLimitMovement);
            return salaryAdvanceLimitMovement;
        }

        @Override
        public boolean existsByLoanApplicationIdAndMovementType(
                UUID loanApplicationId,
                SalaryAdvanceLimitMovementType movementType
        ) {
            return savedMovements.stream()
                    .anyMatch(movement -> loanApplicationId.equals(movement.loanApplicationId())
                            && movementType == movement.movementType());
        }
    }
}

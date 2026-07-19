package com.meridian.platform.loan.application.service;

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
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovedOfferExpiryServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LIMIT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private FakeApprovedOfferRepository approvedOfferRepository;
    private FakeSalaryAdvanceLimitRepository limitRepository;
    private FakeSalaryAdvanceLimitMovementRepository movementRepository;
    private FakeLoanApplicationStatusTransitionRepository transitionRepository;
    private FakeBusinessAuditPublisher auditPublisher;
    private ApprovedOfferExpiryService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        approvedOfferRepository = new FakeApprovedOfferRepository(pendingOffer(NOW));
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
        service = new ApprovedOfferExpiryService(
                loanApplicationRepository,
                approvedOfferRepository,
                releaseService,
                new LoanApplicationStatusTransitionRecorder(transitionRepository),
                auditPublisher
        );
    }

    @Test
    void expiresDuePendingOfferAndReleasesReservation() {
        service.expireDueOffer(LOAN_APPLICATION_ID, systemContext(), ExpiryDiscoveryTrigger.SCHEDULED_SCAN);

        assertEquals(ApprovedOfferStatus.EXPIRED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.EXPIRED, loanApplicationRepository.savedApplication.status());
        assertEquals(1, movementRepository.savedMovements.size());
        assertEquals(money(0), limitRepository.savedLimit.reservedAmount());
        assertEquals(1, transitionRepository.savedTransitions.size());
        assertEquals(ActorType.SYSTEM, transitionRepository.savedTransitions.getFirst().actorType());
        assertEquals(BusinessAuditAction.RESERVATION_RELEASED, auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void skipsPendingOfferBeforeExpiry() {
        approvedOfferRepository.offer = pendingOffer(NOW.plusSeconds(1));

        service.expireDueOffer(LOAN_APPLICATION_ID, systemContext(), ExpiryDiscoveryTrigger.SCHEDULED_SCAN);

        assertNull(approvedOfferRepository.savedOffer);
        assertNull(loanApplicationRepository.savedApplication);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void repeatedExpiryProcessingIsSafe() {
        approvedOfferRepository.offer = pendingOffer(NOW.minusSeconds(1)).expire(NOW.minusSeconds(1));
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.EXPIRED);

        service.expireDueOffer(LOAN_APPLICATION_ID, systemContext(), ExpiryDiscoveryTrigger.SCHEDULED_SCAN);

        assertNull(approvedOfferRepository.savedOffer);
        assertNull(loanApplicationRepository.savedApplication);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void existingReleaseMovementPreventsDuplicateReleaseOnExpiry() {
        movementRepository.releaseMovementExists = true;

        service.expireDueOffer(LOAN_APPLICATION_ID, systemContext(), ExpiryDiscoveryTrigger.SCHEDULED_SCAN);

        assertEquals(ApprovedOfferStatus.EXPIRED, approvedOfferRepository.savedOffer.status());
        assertEquals(LoanApplicationStatus.EXPIRED, loanApplicationRepository.savedApplication.status());
        assertNull(limitRepository.savedLimit);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void scheduledExpiryRejectsUserOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.expireDueOffer(
                        LOAN_APPLICATION_ID,
                        BusinessOperationContext.user(
                                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                                NOW
                        ),
                        ExpiryDiscoveryTrigger.SCHEDULED_SCAN
                )
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(approvedOfferRepository.savedOffer);
        assertNull(loanApplicationRepository.savedApplication);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    private BusinessOperationContext systemContext() {
        return BusinessOperationContext.system(UUID.fromString("abababab-abab-abab-abab-abababababab"), NOW);
    }
    private static ApprovedOffer pendingOffer(LocalDateTime expiresAt) {
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

    private static LoanApplication loanApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                CUSTOMER_ID,
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

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        }

        private LoanApplication application = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        private LoanApplication savedApplication;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplication = loanApplication;
            application = loanApplication;
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findById(UUID loanApplicationId) {
            return Optional.of(application);
        }

        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return Optional.of(application);
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

    private static class FakeApprovedOfferRepository implements ApprovedOfferRepository {

        private ApprovedOffer offer;
        private ApprovedOffer savedOffer;

        private FakeApprovedOfferRepository(ApprovedOffer offer) {
            this.offer = offer;
        }

        @Override
        public ApprovedOffer save(ApprovedOffer approvedOffer) {
            savedOffer = approvedOffer;
            offer = approvedOffer;
            return approvedOffer;
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId) {
            return Optional.of(offer);
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return Optional.of(offer);
        }

        @Override
        public List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize) {
            return List.of(LOAN_APPLICATION_ID);
        }
    }

    private static class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            return salaryAdvanceVerification;
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
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
    }

    private static class FakeSalaryAdvanceLimitRepository implements SalaryAdvanceLimitRepository {

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
        private boolean releaseMovementExists;

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
            return releaseMovementExists || savedMovements.stream()
                    .anyMatch(movement -> loanApplicationId.equals(movement.loanApplicationId())
                            && movementType == movement.movementType());
        }
    }
}

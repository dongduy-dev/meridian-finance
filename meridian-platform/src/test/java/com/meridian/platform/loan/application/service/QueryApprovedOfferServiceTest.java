package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.service.SalaryAdvanceOfferCalculator;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryApprovedOfferServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Test
    void returnsPendingOfferWithAvailableActionsBeforeExpiry() {
        FakeLoanApplicationRepository loanApplicationRepository = new FakeLoanApplicationRepository(CUSTOMER_ID);
        FakeApprovedOfferRepository approvedOfferRepository = new FakeApprovedOfferRepository(pendingOffer(NOW.plusDays(1)));
        QueryApprovedOfferService service = service(loanApplicationRepository, approvedOfferRepository, CUSTOMER_ID);

        ApprovedOfferDto result = service.getApprovedOffer(LOAN_APPLICATION_ID);

        assertEquals("PENDING", result.status());
        assertEquals(List.of("ACCEPT", "DECLINE"), result.availableActions());
        assertEquals(ApprovedOfferStatus.PENDING, approvedOfferRepository.offer.status());
    }

    @Test
    void returnsEffectiveExpiredWithoutMutatingPersistedOfferAtExpiryBoundary() {
        FakeLoanApplicationRepository loanApplicationRepository = new FakeLoanApplicationRepository(CUSTOMER_ID);
        FakeApprovedOfferRepository approvedOfferRepository = new FakeApprovedOfferRepository(pendingOffer(NOW));
        QueryApprovedOfferService service = service(loanApplicationRepository, approvedOfferRepository, CUSTOMER_ID);

        ApprovedOfferDto result = service.getApprovedOffer(LOAN_APPLICATION_ID);

        assertEquals("EXPIRED", result.status());
        assertTrue(result.availableActions().isEmpty());
        assertEquals(ApprovedOfferStatus.PENDING, approvedOfferRepository.offer.status());
        assertTrue(approvedOfferRepository.savedOffers.isEmpty());
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
    }

    @Test
    void enforcesOwnershipThroughLoanApplication() {
        FakeLoanApplicationRepository loanApplicationRepository = new FakeLoanApplicationRepository(
                UUID.fromString("88888888-8888-8888-8888-888888888888")
        );
        QueryApprovedOfferService service = service(
                loanApplicationRepository,
                new FakeApprovedOfferRepository(pendingOffer(NOW.plusDays(1))),
                CUSTOMER_ID
        );

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> service.getApprovedOffer(LOAN_APPLICATION_ID)
        );

        assertEquals("ACCESS_DENIED", exception.getErrorCode());
    }

    private QueryApprovedOfferService service(
            FakeLoanApplicationRepository loanApplicationRepository,
            FakeApprovedOfferRepository approvedOfferRepository,
            UUID currentCustomerId
    ) {
        return new QueryApprovedOfferService(
                loanApplicationRepository,
                approvedOfferRepository,
                new FixedCurrentUserProvider(currentCustomerId),
                new ApprovedOfferMapper(),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
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

    private static BigDecimal money(long value) {
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
                    Set.of("loan:read:own")
            );
        }
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        }

        private final LoanApplication application;
        private final List<LoanApplication> savedApplications = new java.util.ArrayList<>();

        private FakeLoanApplicationRepository(UUID customerId) {
            this.application = new LoanApplication(
                    LOAN_APPLICATION_ID,
                    customerId,
                    UUID.fromString("12121212-1212-1212-1212-121212121212"),
                    "SA-20260706-000001",
                    ProductCode.SALARY_ADVANCE,
                    ProductType.SALARY_BASED,
                    LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                    money(3_000_000),
                    1,
                    NOW.minusDays(2)
            );
        }

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplications.add(loanApplication);
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

        private final ApprovedOffer offer;
        private final List<ApprovedOffer> savedOffers = new java.util.ArrayList<>();

        private FakeApprovedOfferRepository(ApprovedOffer offer) {
            this.offer = offer;
        }

        @Override
        public ApprovedOffer save(ApprovedOffer approvedOffer) {
            savedOffers.add(approvedOffer);
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
            return List.of();
        }
    }
}

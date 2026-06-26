package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartSalaryAdvanceApplicationServiceTest {

    private final UUID customerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final UUID linkId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID partnerCompanyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID partnerEmployeeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private final UUID importBatchId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

    private FakeLoanProductRepository loanProductRepository;
    private FakeLoanApplicationRepository loanApplicationRepository;
    private FakeSalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private FakeSalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private FakeSalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private FakePartnerEligibilityPort partnerEligibilityPort;
    private StartSalaryAdvanceApplicationService service;

    @BeforeEach
    void setUp() {
        loanProductRepository = new FakeLoanProductRepository();
        loanApplicationRepository = new FakeLoanApplicationRepository();
        salaryAdvanceLimitRepository = new FakeSalaryAdvanceLimitRepository();
        salaryAdvanceLimitMovementRepository = new FakeSalaryAdvanceLimitMovementRepository();
        salaryAdvanceVerificationRepository = new FakeSalaryAdvanceVerificationRepository();
        partnerEligibilityPort = new FakePartnerEligibilityPort(verifiedPartnerSnapshot(limit(6_000_000)));
        service = new StartSalaryAdvanceApplicationService(
                loanProductRepository,
                loanApplicationRepository,
                salaryAdvanceLimitRepository,
                salaryAdvanceLimitMovementRepository,
                salaryAdvanceVerificationRepository,
                partnerEligibilityPort,
                new LoanMapper()
        );
    }

    @Test
    void createsSubmittedApplicationAndReservesLimitWithSnapshot() {
        SalaryAdvanceApplicationDto result = service.startSalaryAdvanceApplication(request(limit(3_000_000), 1));

        assertNotNull(result.loanApplicationId());
        assertEquals("SA-20260626-000001".length(), result.applicationNumber().length());
        assertEquals(customerId, result.customerId());
        assertEquals("SALARY_ADVANCE", result.productCode());
        assertEquals("SALARY_BASED", result.productType());
        assertEquals("SUBMITTED", result.status());
        assertEquals(limit(3_000_000), result.requestedAmount());
        assertEquals(1, result.requestedTermMonths());
        assertEquals(linkId, result.customerPartnerEmployeeLinkId());
        assertNotNull(result.salaryAdvanceLimitId());
        assertNotNull(result.salaryAdvanceVerificationId());
        assertEquals("VERIFIED", result.productVerificationResult());
        assertEquals(limit(6_000_000), result.totalLimitSnapshot());
        assertEquals(limit(0), result.usedAmountSnapshot());
        assertEquals(limit(3_000_000), result.reservedAmountSnapshot());
        assertEquals(limit(3_000_000), result.availableLimitSnapshot());
        assertTrue(salaryAdvanceLimitRepository.lockAcquired);
        assertEquals(LoanApplicationStatus.SUBMITTED, loanApplicationRepository.savedApplications.get(0).status());
        assertEquals(2, salaryAdvanceLimitMovementRepository.savedMovements.size());
        assertEquals(SalaryAdvanceLimitMovementType.INITIALIZED,
                salaryAdvanceLimitMovementRepository.savedMovements.get(0).movementType());
        assertEquals(SalaryAdvanceLimitMovementType.RESERVED,
                salaryAdvanceLimitMovementRepository.savedMovements.get(1).movementType());
        assertNotNull(salaryAdvanceVerificationRepository.savedVerification);
    }

    @Test
    void failsWhenVerifiedLinkIsMissing() {
        partnerEligibilityPort.snapshot = Optional.empty();

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("EMPLOYEE_NOT_VERIFIED", exception.getErrorCode());
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
    }

    @Test
    void failsWhenProductIsInactive() {
        loanProductRepository.product = salaryAdvanceProduct(false);

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("PRODUCT_INACTIVE", exception.getErrorCode());
    }

    @Test
    void failsWhenAmountIsBelowProductMinimum() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(100_000), 1))
        );

        assertEquals("INVALID_PRODUCT_AMOUNT", exception.getErrorCode());
    }

    @Test
    void failsWhenAmountIsAboveProductMaximum() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(30_000_000), 1))
        );

        assertEquals("INVALID_PRODUCT_AMOUNT", exception.getErrorCode());
    }

    @Test
    void failsWhenEmployeeConfiguredLimitIsInsufficient() {
        partnerEligibilityPort.snapshot = Optional.of(verifiedPartnerSnapshot(limit(2_000_000)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("INSUFFICIENT_AVAILABLE_LIMIT", exception.getErrorCode());
    }

    @Test
    void failsWhenCurrentAvailableLimitIsInsufficient() {
        salaryAdvanceLimitRepository.currentLimit = Optional.of(new SalaryAdvanceLimit(
                UUID.randomUUID(),
                customerId,
                linkId,
                limit(6_000_000),
                limit(0),
                limit(5_000_000),
                limit(1_000_000),
                com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus.ACTIVE,
                LocalDateTime.now()
        ));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("INSUFFICIENT_AVAILABLE_LIMIT", exception.getErrorCode());
    }

    @Test
    void failsWhenBlockingApplicationExists() {
        loanApplicationRepository.blockingApplicationExists = true;

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("BLOCKING_APPLICATION_EXISTS", exception.getErrorCode());
    }

    @Test
    void failsWhenTermIsNotAllowed() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 6))
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    private SalaryAdvanceApplicationRequest request(BigDecimal requestedAmount, int requestedTermMonths) {
        return new SalaryAdvanceApplicationRequest(customerId, linkId, requestedAmount, requestedTermMonths);
    }

    private VerifiedPartnerEmployeeLinkSnapshot verifiedPartnerSnapshot(BigDecimal employeeSalaryAdvanceLimit) {
        return new VerifiedPartnerEmployeeLinkSnapshot(
                customerId,
                linkId,
                partnerCompanyId,
                partnerEmployeeId,
                importBatchId,
                employeeSalaryAdvanceLimit,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private LoanProduct salaryAdvanceProduct(boolean active) {
        return new LoanProduct(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                "Salary Advance",
                null,
                active,
                limit(500_000),
                limit(20_000_000)
        );
    }

    private BigDecimal limit(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private class FakeLoanProductRepository implements LoanProductRepository {

        private LoanProduct product = salaryAdvanceProduct(true);

        @Override
        public List<LoanProduct> findAllActive() {
            return product.active() ? List.of(product) : List.of();
        }

        @Override
        public Optional<LoanProduct> findByProductCode(ProductCode productCode) {
            if (product.productCode() != productCode) {
                return Optional.empty();
            }
            return Optional.of(product);
        }
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {

        private final List<LoanApplication> savedApplications = new ArrayList<>();
        private boolean blockingApplicationExists;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplications.add(loanApplication);
            return loanApplication;
        }

        @Override
        public boolean existsByCustomerIdAndProductCodeAndStatusIn(
                UUID customerId,
                ProductCode productCode,
                Set<LoanApplicationStatus> statuses
        ) {
            return blockingApplicationExists;
        }

        @Override
        public long nextApplicationNumberSequence() {
            return 1L;
        }
    }

    private static class FakeSalaryAdvanceLimitRepository implements SalaryAdvanceLimitRepository {

        private Optional<SalaryAdvanceLimit> currentLimit = Optional.empty();
        private boolean lockAcquired;

        @Override
        public void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId) {
            lockAcquired = true;
        }

        @Override
        public Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            return currentLimit;
        }

        @Override
        public SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit) {
            currentLimit = Optional.of(salaryAdvanceLimit);
            return salaryAdvanceLimit;
        }
    }

    private static class FakeSalaryAdvanceLimitMovementRepository implements SalaryAdvanceLimitMovementRepository {

        private final List<SalaryAdvanceLimitMovement> savedMovements = new ArrayList<>();

        @Override
        public SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement) {
            savedMovements.add(salaryAdvanceLimitMovement);
            return salaryAdvanceLimitMovement;
        }
    }

    private static class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        private SalaryAdvanceVerification savedVerification;

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            savedVerification = salaryAdvanceVerification;
            return salaryAdvanceVerification;
        }
    }

    private static class FakePartnerEligibilityPort implements PartnerEligibilityPort {

        private Optional<VerifiedPartnerEmployeeLinkSnapshot> snapshot;

        private FakePartnerEligibilityPort(VerifiedPartnerEmployeeLinkSnapshot snapshot) {
            this.snapshot = Optional.of(snapshot);
        }

        @Override
        public Optional<VerifiedPartnerEmployeeLinkSnapshot> findVerifiedEmployeeLink(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            return snapshot
                    .filter(value -> value.customerId().equals(customerId))
                    .filter(value -> value.customerPartnerEmployeeLinkId().equals(customerPartnerEmployeeLinkId));
        }
    }
}

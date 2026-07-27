package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    private FakeLoanDocumentChecklistPort documentChecklistPort;
    private FakeSalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private FakeSalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private FakeSalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private FakeCustomerReadinessPort customerReadinessPort;
    private FakePartnerEligibilityPort partnerEligibilityPort;
    private FakeLoanApplicationStatusTransitionRepository transitionRepository;
    private FakeBusinessAuditPublisher auditPublisher;
    private StartSalaryAdvanceApplicationService service;

    @BeforeEach
    void setUp() {
        loanProductRepository = new FakeLoanProductRepository();
        loanApplicationRepository = new FakeLoanApplicationRepository();
        documentChecklistPort = new FakeLoanDocumentChecklistPort();
        salaryAdvanceLimitRepository = new FakeSalaryAdvanceLimitRepository();
        salaryAdvanceLimitMovementRepository = new FakeSalaryAdvanceLimitMovementRepository();
        salaryAdvanceVerificationRepository = new FakeSalaryAdvanceVerificationRepository();
        customerReadinessPort = new FakeCustomerReadinessPort(readiness(true, true, true, "UNVERIFIED"));
        partnerEligibilityPort = new FakePartnerEligibilityPort(verifiedPartnerSnapshot(limit(6_000_000)));
        transitionRepository = new FakeLoanApplicationStatusTransitionRepository();
        auditPublisher = new FakeBusinessAuditPublisher();
        service = new StartSalaryAdvanceApplicationService(
                loanProductRepository,
                loanApplicationRepository,
                documentChecklistPort,
                salaryAdvanceLimitRepository,
                salaryAdvanceLimitMovementRepository,
                salaryAdvanceVerificationRepository,
                customerReadinessPort,
                partnerEligibilityPort,
                new LoanMapper(),
                new FixedCurrentUserProvider(customerId),
                new LoanApplicationStatusTransitionRecorder(transitionRepository),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-06-26T05:00:00Z"), ZoneOffset.UTC)
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
        assertTrue(documentChecklistPort.checklistCreated);
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
        assertTrue(loanApplicationRepository.customerProductLockAcquired);
        assertEquals(LoanApplicationStatus.SUBMITTED, loanApplicationRepository.savedApplications.get(0).status());
        assertEquals(2, salaryAdvanceLimitMovementRepository.savedMovements.size());
        assertEquals(SalaryAdvanceLimitMovementType.INITIALIZED,
                salaryAdvanceLimitMovementRepository.savedMovements.get(0).movementType());
        assertEquals(SalaryAdvanceLimitMovementType.RESERVED,
                salaryAdvanceLimitMovementRepository.savedMovements.get(1).movementType());
        assertNotNull(salaryAdvanceVerificationRepository.savedVerification);
        assertEquals(SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                salaryAdvanceVerificationRepository.savedVerification.employeeVerificationOutcome());
        assertEquals(1, transitionRepository.savedTransitions.size());
        LoanApplicationStatusTransition transition = transitionRepository.savedTransitions.getFirst();
        assertEquals(LoanApplicationStatus.SUBMITTED, transition.toStatus());
        assertEquals(ActorType.USER, transition.actorType());
        assertEquals(3, auditPublisher.lastEvent().entries().size());
        assertEquals(List.of(
                        BusinessAuditAction.SALARY_ADVANCE_APPLICATION_SUBMITTED,
                        BusinessAuditAction.SALARY_ADVANCE_LIMIT_INITIALIZED,
                        BusinessAuditAction.SALARY_ADVANCE_LIMIT_RESERVED
                ),
                auditPublisher.lastEvent().entries().stream().map(entry -> entry.action()).toList());
    }

    @Test
    void startsInDocumentsPendingWhenResolvedChecklistNeedsAnUpload() {
        documentChecklistPort.uploadComplete = false;

        SalaryAdvanceApplicationDto result = service.startSalaryAdvanceApplication(request(limit(3_000_000), 1));

        assertEquals("DOCUMENTS_PENDING", result.status());
        assertEquals(LoanApplicationStatus.DOCUMENTS_PENDING, transitionRepository.savedTransitions.getFirst().toStatus());
    }

    @Test
    void failsBeforeLoanSideEffectsWhenCustomerIsMissing() {
        customerReadinessPort.snapshot = Optional.empty();

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("CUSTOMER_NOT_FOUND", exception.getErrorCode());
        assertNoLoanSideEffectsBeforeReadinessPasses();
    }

    @Test
    void failsBeforeLoanSideEffectsWhenCustomerIsNotActive() {
        customerReadinessPort.snapshot = Optional.of(readiness(false, true, true, "VERIFIED"));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("CUSTOMER_NOT_ACTIVE", exception.getErrorCode());
        assertNoLoanSideEffectsBeforeReadinessPasses();
    }

    @Test
    void failsBeforeLoanSideEffectsWhenProfileIsIncomplete() {
        customerReadinessPort.snapshot = Optional.of(readiness(true, false, true, "UNVERIFIED"));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("PROFILE_INCOMPLETE", exception.getErrorCode());
        assertNoLoanSideEffectsBeforeReadinessPasses();
    }

    @Test
    void failsBeforeLoanSideEffectsWhenPrimaryBankAccountIsMissing() {
        customerReadinessPort.snapshot = Optional.of(readiness(true, true, false, "UNVERIFIED"));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("PRIMARY_BANK_ACCOUNT_REQUIRED", exception.getErrorCode());
        assertNoLoanSideEffectsBeforeReadinessPasses();
    }
    @Test
    void createsLimitUsingFullCapFormula() {
        partnerEligibilityPort.snapshot = Optional.of(verifiedPartnerSnapshot(
                limit(12_000_000),
                limit(20_000_000),
                limit(10_000_000)
        ));

        SalaryAdvanceApplicationDto result = service.startSalaryAdvanceApplication(request(limit(3_000_000), 1));

        assertEquals(limit(4_000_000), result.totalLimitSnapshot());
        assertEquals(limit(3_000_000), result.reservedAmountSnapshot());
        assertEquals(limit(1_000_000), result.availableLimitSnapshot());
        assertEquals(limit(4_000_000), salaryAdvanceLimitRepository.currentLimit.orElseThrow().totalLimit());
        assertEquals(limit(4_000_000), salaryAdvanceLimitMovementRepository.savedMovements.get(0).amount());
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
    void rejectsFractionalVndBeforeAnyLoanWorkflowPersistence() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(new BigDecimal("3000000.50"), 1))
        );

        assertEquals("INVALID_PRODUCT_AMOUNT", exception.getErrorCode());
        assertEquals("Requested amount must be a whole VND amount.", exception.getMessage());
        assertEquals(0, loanApplicationRepository.existsChecks);
        assertEquals(0, partnerEligibilityPort.findCalls);
        assertFalse(salaryAdvanceLimitRepository.lockAcquired);
        assertFalse(loanApplicationRepository.customerProductLockAcquired);
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
        assertTrue(salaryAdvanceLimitRepository.currentLimit.isEmpty());
        assertTrue(salaryAdvanceLimitMovementRepository.savedMovements.isEmpty());
        assertTrue(salaryAdvanceVerificationRepository.savedVerification == null);
        assertTrue(transitionRepository.savedTransitions.isEmpty());
        assertTrue(auditPublisher.publishedEvents.isEmpty());
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
                SalaryAdvanceLimitStatus.ACTIVE,
                LocalDateTime.now()
        ));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("INSUFFICIENT_AVAILABLE_LIMIT", exception.getErrorCode());
    }

    @Test
    void failsWhenRequestedAmountExceedsEffectiveCapEvenIfStoredLimitIsHigher() {
        partnerEligibilityPort.snapshot = Optional.of(verifiedPartnerSnapshot(
                limit(12_000_000),
                limit(20_000_000),
                limit(10_000_000)
        ));
        salaryAdvanceLimitRepository.currentLimit = Optional.of(new SalaryAdvanceLimit(
                UUID.randomUUID(),
                customerId,
                linkId,
                limit(8_000_000),
                limit(0),
                limit(0),
                limit(8_000_000),
                SalaryAdvanceLimitStatus.ACTIVE,
                LocalDateTime.now()
        ));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(5_000_000), 1))
        );

        assertEquals("INSUFFICIENT_AVAILABLE_LIMIT", exception.getErrorCode());
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
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
    void failsWhenBlockingApplicationAppearsAfterLimitLock() {
        loanApplicationRepository.blockingApplicationResults.add(false);
        loanApplicationRepository.blockingApplicationResults.add(true);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 1))
        );

        assertEquals("BLOCKING_APPLICATION_EXISTS", exception.getErrorCode());
        assertTrue(salaryAdvanceLimitRepository.lockAcquired);
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
        assertEquals(2, loanApplicationRepository.existsChecks);
    }

    @Test
    void returnedForRevisionBlocksDuplicates() {
        assertTrue(LoanApplicationStatus.blockingStatuses().contains(LoanApplicationStatus.RETURNED_FOR_REVISION));
    }

    @Test
    void failsWhenTermIsNotAllowed() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startSalaryAdvanceApplication(request(limit(3_000_000), 6))
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    private void assertNoLoanSideEffectsBeforeReadinessPasses() {
        assertEquals(0, loanProductRepository.findByProductCodeCalls);
        assertEquals(0, partnerEligibilityPort.findCalls);
        assertFalse(salaryAdvanceLimitRepository.lockAcquired);
        assertFalse(loanApplicationRepository.customerProductLockAcquired);
        assertTrue(loanApplicationRepository.savedApplications.isEmpty());
        assertTrue(salaryAdvanceLimitMovementRepository.savedMovements.isEmpty());
        assertTrue(salaryAdvanceVerificationRepository.savedVerification == null);
        assertTrue(transitionRepository.savedTransitions.isEmpty());
        assertTrue(auditPublisher.publishedEvents.isEmpty());
    }

    private SalaryAdvanceApplicationRequest request(BigDecimal requestedAmount, int requestedTermMonths) {
        return new SalaryAdvanceApplicationRequest(linkId, requestedAmount, requestedTermMonths);
    }

    private CustomerReadinessSnapshot readiness(
            boolean active,
            boolean profileComplete,
            boolean hasPrimaryActiveBankAccount,
            String verificationStatus
    ) {
        return new CustomerReadinessSnapshot(
                customerId,
                active,
                profileComplete,
                hasPrimaryActiveBankAccount,
                verificationStatus
        );
    }

    private VerifiedPartnerEmployeeLinkSnapshot verifiedPartnerSnapshot(BigDecimal employeeSalaryAdvanceLimit) {
        return verifiedPartnerSnapshot(employeeSalaryAdvanceLimit, limit(10_000_000), limit(18_000_000));
    }

    private VerifiedPartnerEmployeeLinkSnapshot verifiedPartnerSnapshot(
            BigDecimal employeeSalaryAdvanceLimit,
            BigDecimal partnerCompanySalaryAdvanceLimit,
            BigDecimal employeeSalaryAmount
    ) {
        return new VerifiedPartnerEmployeeLinkSnapshot(
                customerId,
                linkId,
                partnerCompanyId,
                partnerEmployeeId,
                importBatchId,
                SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                partnerCompanySalaryAdvanceLimit,
                employeeSalaryAmount,
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
                    Set.of("loan:submit")
            );
        }
    }

    private class FakeLoanProductRepository implements LoanProductRepository {

        private LoanProduct product = salaryAdvanceProduct(true);
        private int findByProductCodeCalls;

        @Override
        public List<LoanProduct> findAllActive() {
            return product.active() ? List.of(product) : List.of();
        }

        @Override
        public Optional<LoanProduct> findByProductCode(ProductCode productCode) {
            findByProductCodeCalls++;
            if (product.productCode() != productCode) {
                return Optional.empty();
            }
            return Optional.of(product);
        }
    }

    private static class FakeLoanDocumentChecklistPort implements LoanDocumentChecklistPort {

        private boolean uploadComplete = true;
        private boolean checklistCreated;

        @Override
        public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
            return new SubmissionChecklistInitialState(uploadComplete);
        }

        @Override
        public void createSubmissionChecklist(
                UUID loanApplicationId,
                ProductCode productCode,
                com.meridian.platform.shared.application.operation.BusinessOperationContext operationContext
        ) {
            checklistCreated = true;
        }

        @Override
        public boolean isProcessingReady(UUID loanApplicationId) {
            return true;
        }
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        private boolean customerProductLockAcquired;

        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
            customerProductLockAcquired = true;
        }

        private final List<LoanApplication> savedApplications = new ArrayList<>();
        private final Deque<Boolean> blockingApplicationResults = new ArrayDeque<>();
        private boolean blockingApplicationExists;
        private int existsChecks;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplications.add(loanApplication);
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findById(UUID loanApplicationId) {
            return Optional.empty();
        }
        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return Optional.empty();
        }

        @Override
        public boolean existsByCustomerIdAndProductCodeAndStatusIn(
                UUID customerId,
                ProductCode productCode,
                Set<LoanApplicationStatus> statuses
        ) {
            if (!customerProductLockAcquired) {
                throw new AssertionError("Blocking check occurred before customer-product lock acquisition.");
            }
            existsChecks++;
            if (!blockingApplicationResults.isEmpty()) {
                return blockingApplicationResults.removeFirst();
            }
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
            return false;
        }    }

    private static class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        private SalaryAdvanceVerification savedVerification;

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            savedVerification = salaryAdvanceVerification;
            return salaryAdvanceVerification;
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
            return Optional.ofNullable(savedVerification)
                    .filter(value -> value.loanApplicationId().equals(loanApplicationId));
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return findByLoanApplicationId(loanApplicationId);
        }
    }

    private static class FakeCustomerReadinessPort implements CustomerReadinessPort {

        private Optional<CustomerReadinessSnapshot> snapshot;

        private FakeCustomerReadinessPort(CustomerReadinessSnapshot snapshot) {
            this.snapshot = Optional.of(snapshot);
        }

        @Override
        public Optional<CustomerReadinessSnapshot> findReadinessByCustomerId(UUID customerId) {
            return snapshot.filter(value -> value.customerId().equals(customerId));
        }
    }
    private static class FakePartnerEligibilityPort implements PartnerEligibilityPort {

        private Optional<VerifiedPartnerEmployeeLinkSnapshot> snapshot;
        private int findCalls;

        private FakePartnerEligibilityPort(VerifiedPartnerEmployeeLinkSnapshot snapshot) {
            this.snapshot = Optional.of(snapshot);
        }

        @Override
        public Optional<VerifiedPartnerEmployeeLinkSnapshot> findVerifiedEmployeeLink(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            findCalls++;
            return snapshot
                    .filter(value -> value.customerId().equals(customerId))
                    .filter(value -> value.customerPartnerEmployeeLinkId().equals(customerPartnerEmployeeLinkId));
        }
    }
}

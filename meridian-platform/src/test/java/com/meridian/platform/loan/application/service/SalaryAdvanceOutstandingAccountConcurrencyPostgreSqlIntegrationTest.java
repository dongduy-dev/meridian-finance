package com.meridian.platform.loan.application.service;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = {
        MeridianPlatformApplication.class,
        SalaryAdvanceOutstandingAccountConcurrencyPostgreSqlIntegrationTest.UserConfiguration.class
}, properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class SalaryAdvanceOutstandingAccountConcurrencyPostgreSqlIntegrationTest {

    private static final String SCHEMA = "outstanding_submission_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID PARTNER_COMPANY_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARTNER_EMPLOYEE_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private static final UUID IMPORT_BATCH_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired CloseLoanAccountUseCase closures;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired StartSalaryAdvanceApplicationUseCase submissions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ThreadLocalCurrentUser currentUser;
    @MockitoSpyBean SalaryAdvanceLimitRepository limitRepository;
    @Autowired OutstandingLoanAccountQuery outstandingAccounts;

    private ManualDisbursementActivationPostgreSqlTestSupport support;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void setUp() {
        reset(limitRepository);
        jdbc.update("update partner_employee_import_batches set effective_month='2026-08' where id=?",
                IMPORT_BATCH_ID);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
    }

    @Test
    void activeAndOverdueDebtBlockWhileFullPayoffImmediatelyAllowsSubmission() {
        Activated activated = activateAndPrepareCustomer();
        currentUser.customer(activated.userId(), activated.fixture().customerId());

        BusinessStateConflictException activeConflict = conflictOnSubmit(activated.fixture().linkId());
        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", activeConflict.getErrorCode());
        assertTrue(!activeConflict.getMessage().contains(activated.accountId().toString()));
        assertTrue(!activeConflict.getMessage().contains("1100"));
        assertTrue(!activeConflict.getMessage().contains(
                activated.fixture().customerId().toString()));
        assertTrue(!activeConflict.getMessage().contains(
                activated.fixture().linkId().toString()));
        assertTrue(!activeConflict.getMessage().contains(
                activated.fixture().limitId().toString()));
        assertTrue(LocalDate.now(Clock.fixed(
                Instant.parse("2026-08-29T10:00:00Z"), ZoneOffset.UTC)).isAfter(
                jdbc.queryForObject("select min(due_date) from repayment_schedule_items item "
                                + "join repayment_schedules schedule on schedule.id="
                                + "item.repayment_schedule_id where schedule.loan_account_id=?",
                        LocalDate.class, activated.accountId())));

        overdueEvaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                activated.fixture().applicationId(), activated.accountId(),
                java.time.LocalDate.of(2026, 8, 29),
                java.time.LocalDateTime.of(2026, 8, 29, 0, 5)
        ));
        BusinessStateConflictException overdueConflict = conflictOnSubmit(activated.fixture().linkId());
        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", overdueConflict.getErrorCode());

        currentUser.staff();
        repayFully(activated);
        currentUser.customer(activated.userId(), activated.fixture().customerId());
        SalaryAdvanceApplicationDto result = submit(activated.fixture().linkId());
        assertNotNull(result.loanApplicationId());
        assertEquals("SETTLED", status(activated.accountId()));
    }

    @Test
    void partialPrincipalReleaseRestoresAvailabilityButOutstandingDebtStillBlocks() {
        Activated activated = activateAndPrepareCustomer();
        BigDecimal availableBefore = jdbc.queryForObject(
                "select available_amount from salary_advance_limits where id=?",
                BigDecimal.class, activated.fixture().limitId());
        currentUser.staff();
        RecordRepaymentUseCase.Result repayment = repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), activated.fixture().applicationId(),
                        "PARTIAL-GUARD-" + UUID.randomUUID(), money("100"),
                        LocalDate.of(2026, 8, 29)));
        assertEquals(0, money("50").compareTo(
                repayment.principalAllocatedAndReleased()));
        assertEquals(0, availableBefore.add(money("50")).compareTo(
                jdbc.queryForObject("select available_amount from salary_advance_limits "
                                + "where id=?", BigDecimal.class,
                        activated.fixture().limitId())));

        currentUser.customer(activated.userId(), activated.fixture().customerId());
        BusinessStateConflictException conflict = conflictOnSubmit(activated.fixture().linkId());
        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", conflict.getErrorCode());
        assertSafeConflict(conflict, activated);
    }

    @Test
    void differentEmployeeLinkStillUsesAuthoritativeCustomerProductScope() {
        Activated activated = activateAndPrepareCustomer();
        UUID otherLinkId = UUID.randomUUID();
        jdbc.update("update customer_partner_employee_links set link_status='SUSPENDED' "
                + "where id=?", activated.fixture().linkId());
        jdbc.update("insert into customer_partner_employee_links "
                        + "(id,customer_id,partner_company_id,partner_employee_id,"
                        + "source_import_batch_id,verification_outcome,link_status,"
                        + "verified_identity_ref,verified_employee_code,last_verified_at,"
                        + "last_refreshed_at) values (?,?,?,?,?,'MATCHED_ACTIVE','VERIFIED',"
                        + "?,'MER-EMP-002',?,?)",
                otherLinkId, activated.fixture().customerId(), PARTNER_COMPANY_ID,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02"), IMPORT_BATCH_ID,
                "verified-other-" + otherLinkId,
                ManualDisbursementActivationPostgreSqlTestSupport.NOW,
                ManualDisbursementActivationPostgreSqlTestSupport.NOW);
        currentUser.customer(activated.userId(), activated.fixture().customerId());
        BusinessStateConflictException conflict = conflictOnSubmit(otherLinkId);
        assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", conflict.getErrorCode());
        assertSafeConflict(conflict, activated);
    }

    @Test
    void databaseRejectsClosedAccountWithOutstandingDebt() {
        Activated activated = activateAndPrepareCustomer();
        String originalStatus = status(activated.accountId());

        assertThrows(DataIntegrityViolationException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
                        jdbc.update("update loan_accounts set status='CLOSED' where id=?",
                                activated.accountId())));

        assertEquals(originalStatus, status(activated.accountId()));
    }

    @Test
    void outstandingGuardDoesNotCrossCustomerBoundary() {
        activateAndPrepareCustomer();
        assertEquals(OutstandingLoanAccountQuery.GuardResult.CLEAR,
                outstandingAccounts.inspect(UUID.randomUUID(), ProductCode.SALARY_ADVANCE));
    }


    @Test
    void submissionObservesOutstandingDebtBeforeRepaymentTakesLinkLock() throws Exception {
        Activated activated = activateAndPrepareCustomer();
        int applicationsBefore = customerApplications(activated);
        int reservationsBefore = reservationMovements(activated);
        int snapshotsBefore = verificationSnapshots(activated);
        int auditsBefore = auditCount();
        CountDownLatch repaymentAtBoundary = new CountDownLatch(1);
        CountDownLatch releaseRepayment = new CountDownLatch(1);
        installCustomerLinkBarrier(activated, "payoff-before-link", false,
                repaymentAtBoundary, releaseRepayment, null, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RecordRepaymentUseCase.Result> repayment = pool.submit(() -> {
                Thread.currentThread().setName("payoff-before-link");
                currentUser.staff();
                return repayFully(activated);
            });
            assertTrue(repaymentAtBoundary.await(5, TimeUnit.SECONDS));
            Future<Object> submission = pool.submit(() -> {
                Thread.currentThread().setName("submission-observes-outstanding");
                currentUser.customer(activated.userId(), activated.fixture().customerId());
                try {
                    return submit(activated.fixture().linkId());
                } catch (BusinessStateConflictException conflict) {
                    return conflict;
                }
            });
            Object submissionOutcome = submission.get(15, TimeUnit.SECONDS);
            BusinessStateConflictException conflict = assertInstanceOf(
                    BusinessStateConflictException.class, submissionOutcome);
            assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", conflict.getErrorCode());
            assertFalse(repayment.isDone());
            assertEquals(applicationsBefore, customerApplications(activated));
            assertEquals(reservationsBefore, reservationMovements(activated));
            assertEquals(snapshotsBefore, verificationSnapshots(activated));
            assertEquals(auditsBefore, auditCount());

            releaseRepayment.countDown();
            repayment.get(15, TimeUnit.SECONDS);
            assertEquals("SETTLED", status(activated.accountId()));
            currentUser.customer(activated.userId(), activated.fixture().customerId());
            assertNotNull(submit(activated.fixture().linkId()).loanApplicationId());
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from salary_advance_limit_movements "
                            + "where repayment_transaction_id is not null "
                            + "and movement_type='REPAID_RELEASED' and loan_application_id=?",
                    Integer.class, activated.fixture().applicationId()));
        } finally {
            releaseRepayment.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void repaymentSettlesUnderLinkLockBeforeSubmissionReserves() throws Exception {
        Activated activated = activateAndPrepareCustomer();
        int applicationsBefore = customerApplications(activated);
        int reservationsBefore = reservationMovements(activated);
        CountDownLatch repaymentHasLinkLock = new CountDownLatch(1);
        CountDownLatch releaseRepayment = new CountDownLatch(1);
        CountDownLatch submissionHasLinkLock = new CountDownLatch(1);
        installCustomerLinkBarrier(activated, "payoff-holds-link", true,
                repaymentHasLinkLock, releaseRepayment,
                "submission-waits-link", submissionHasLinkLock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RecordRepaymentUseCase.Result> repayment = pool.submit(() -> {
                Thread.currentThread().setName("payoff-holds-link");
                currentUser.staff();
                return repayFully(activated);
            });
            assertTrue(repaymentHasLinkLock.await(5, TimeUnit.SECONDS));
            Future<SalaryAdvanceApplicationDto> submission = pool.submit(() -> {
                Thread.currentThread().setName("submission-waits-link");
                currentUser.customer(activated.userId(), activated.fixture().customerId());
                return submit(activated.fixture().linkId());
            });
            assertFalse(submissionHasLinkLock.await(300, TimeUnit.MILLISECONDS));
            assertFalse(submission.isDone());
            assertEquals(applicationsBefore, customerApplications(activated));
            assertEquals(reservationsBefore, reservationMovements(activated));

            releaseRepayment.countDown();
            RecordRepaymentUseCase.Result repaymentResult =
                    repayment.get(15, TimeUnit.SECONDS);
            SalaryAdvanceApplicationDto submissionResult =
                    submission.get(15, TimeUnit.SECONDS);
            assertNotNull(submissionResult.loanApplicationId());
            assertEquals("SETTLED", status(activated.accountId()));
            assertEquals(1, count("select count(*) from salary_advance_limit_movements "
                    + "where repayment_transaction_id=? and movement_type='REPAID_RELEASED'",
                    repaymentResult.repaymentTransactionId()));
            assertEquals(applicationsBefore + 1, customerApplications(activated));
            assertEquals(reservationsBefore + 1, reservationMovements(activated));
            Map<String, BigDecimal> limit = jdbc.queryForMap(
                    "select total_limit,available_amount,reserved_amount,used_amount "
                            + "from salary_advance_limits where id=?",
                    activated.fixture().limitId()).entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            entry -> (BigDecimal) entry.getValue()));
            assertEquals(0, money("500000").compareTo(limit.get("reserved_amount")));
            assertEquals(0, money("0").compareTo(limit.get("used_amount")));
            assertEquals(0, limit.get("total_limit").compareTo(
                    limit.get("available_amount").add(limit.get("reserved_amount"))
                            .add(limit.get("used_amount"))));
            assertEquals(1, count("select count(*) from repayment_transactions "
                    + "where loan_account_id=?", activated.accountId()));
        } finally {
            releaseRepayment.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void submissionObservesOutstandingDebtBeforeSettlementTakesLinkLock() throws Exception {
        Activated activated = activateAndPrepareCustomer();
        int applicationsBefore = customerApplications(activated);
        int reservationsBefore = reservationMovements(activated);
        int snapshotsBefore = verificationSnapshots(activated);
        int auditsBefore = auditCount();
        CountDownLatch settlementAtBoundary = new CountDownLatch(1);
        CountDownLatch releaseSettlement = new CountDownLatch(1);
        installCustomerLinkBarrier(activated, "settlement-before-link", false,
                settlementAtBoundary, releaseSettlement, null, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ApproveLoanSettlementUseCase.Result> settlement = pool.submit(() -> {
                Thread.currentThread().setName("settlement-before-link");
                currentUser.approver();
                return settleFully(activated);
            });
            assertTrue(settlementAtBoundary.await(5, TimeUnit.SECONDS));
            Future<Object> submission = pool.submit(() -> {
                Thread.currentThread().setName("submission-observes-settlement");
                currentUser.customer(activated.userId(), activated.fixture().customerId());
                try {
                    return submit(activated.fixture().linkId());
                } catch (BusinessStateConflictException conflict) {
                    return conflict;
                }
            });
            BusinessStateConflictException conflict = assertInstanceOf(
                    BusinessStateConflictException.class,
                    submission.get(15, TimeUnit.SECONDS)
            );
            assertEquals("OUTSTANDING_LOAN_ACCOUNT_EXISTS", conflict.getErrorCode());
            assertFalse(settlement.isDone());
            assertEquals(applicationsBefore, customerApplications(activated));
            assertEquals(reservationsBefore, reservationMovements(activated));
            assertEquals(snapshotsBefore, verificationSnapshots(activated));
            assertEquals(auditsBefore, auditCount());

            releaseSettlement.countDown();
            ApproveLoanSettlementUseCase.Result result =
                    settlement.get(15, TimeUnit.SECONDS);
            assertEquals("SETTLED", status(activated.accountId()));
            currentUser.customer(activated.userId(), activated.fixture().customerId());
            assertNotNull(submit(activated.fixture().linkId()).loanApplicationId());
            assertEquals(1, count("select count(*) from approved_loan_settlements "
                    + "where repayment_transaction_id=?",
                    result.repaymentTransactionId()));
            assertEquals(1, count("select count(*) from salary_advance_limit_movements "
                    + "where repayment_transaction_id=? "
                    + "and movement_type='REPAID_RELEASED'",
                    result.repaymentTransactionId()));
        } finally {
            releaseSettlement.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void settlementCompletesUnderLinkLockBeforeSubmissionReserves() throws Exception {
        Activated activated = activateAndPrepareCustomer();
        int applicationsBefore = customerApplications(activated);
        int reservationsBefore = reservationMovements(activated);
        CountDownLatch settlementHasLinkLock = new CountDownLatch(1);
        CountDownLatch releaseSettlement = new CountDownLatch(1);
        CountDownLatch submissionHasLinkLock = new CountDownLatch(1);
        installCustomerLinkBarrier(activated, "settlement-holds-link", true,
                settlementHasLinkLock, releaseSettlement,
                "submission-waits-settlement", submissionHasLinkLock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ApproveLoanSettlementUseCase.Result> settlement = pool.submit(() -> {
                Thread.currentThread().setName("settlement-holds-link");
                currentUser.approver();
                return settleFully(activated);
            });
            assertTrue(settlementHasLinkLock.await(5, TimeUnit.SECONDS));
            Future<SalaryAdvanceApplicationDto> submission = pool.submit(() -> {
                Thread.currentThread().setName("submission-waits-settlement");
                currentUser.customer(activated.userId(), activated.fixture().customerId());
                return submit(activated.fixture().linkId());
            });
            assertFalse(submissionHasLinkLock.await(300, TimeUnit.MILLISECONDS));
            assertFalse(submission.isDone());
            assertEquals(applicationsBefore, customerApplications(activated));
            assertEquals(reservationsBefore, reservationMovements(activated));

            releaseSettlement.countDown();
            ApproveLoanSettlementUseCase.Result settlementResult =
                    settlement.get(15, TimeUnit.SECONDS);
            SalaryAdvanceApplicationDto submissionResult =
                    submission.get(15, TimeUnit.SECONDS);
            assertNotNull(submissionResult.loanApplicationId());
            assertEquals("SETTLED", status(activated.accountId()));
            assertEquals(1, count("select count(*) from approved_loan_settlements "
                    + "where repayment_transaction_id=?",
                    settlementResult.repaymentTransactionId()));
            assertEquals(1, count("select count(*) from salary_advance_limit_movements "
                    + "where repayment_transaction_id=? "
                    + "and movement_type='REPAID_RELEASED'",
                    settlementResult.repaymentTransactionId()));
            assertEquals(applicationsBefore + 1, customerApplications(activated));
            assertEquals(reservationsBefore + 1, reservationMovements(activated));
            Map<String, BigDecimal> limit = jdbc.queryForMap(
                    "select total_limit,available_amount,reserved_amount,used_amount "
                            + "from salary_advance_limits where id=?",
                    activated.fixture().limitId()).entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            entry -> (BigDecimal) entry.getValue()));
            assertEquals(0, money("500000").compareTo(limit.get("reserved_amount")));
            assertEquals(0, money("0").compareTo(limit.get("used_amount")));
            assertEquals(0, limit.get("total_limit").compareTo(
                    limit.get("available_amount").add(limit.get("reserved_amount"))
                            .add(limit.get("used_amount"))));
        } finally {
            releaseSettlement.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closureDoesNotChangeSubmissionEligibilityInEitherOrderOrWhenConcurrent()
            throws Exception {
        Activated closureFirst = activateAndPrepareCustomer();
        currentUser.approver();
        settleFully(closureFirst);
        currentUser.staff();
        closures.close(new CloseLoanAccountUseCase.Command(
                UUID.randomUUID(),
                closureFirst.fixture().applicationId()
        ));
        currentUser.customer(
                closureFirst.userId(),
                closureFirst.fixture().customerId()
        );
        assertNotNull(submit(closureFirst.fixture().linkId()).loanApplicationId());
        assertEquals("CLOSED", status(closureFirst.accountId()));

        Activated submissionFirst = activateAndPrepareCustomer();
        currentUser.approver();
        settleFully(submissionFirst);
        currentUser.customer(
                submissionFirst.userId(),
                submissionFirst.fixture().customerId()
        );
        assertNotNull(submit(submissionFirst.fixture().linkId()).loanApplicationId());
        currentUser.staff();
        closures.close(new CloseLoanAccountUseCase.Command(
                UUID.randomUUID(),
                submissionFirst.fixture().applicationId()
        ));
        assertEquals("CLOSED", status(submissionFirst.accountId()));

        Activated concurrent = activateAndPrepareCustomer();
        currentUser.approver();
        settleFully(concurrent);
        int applicationsBefore = customerApplications(concurrent);
        int reservationsBefore = reservationMovements(concurrent);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CloseLoanAccountUseCase.Result> closure = pool.submit(() -> {
                currentUser.staff();
                start.await();
                return closures.close(new CloseLoanAccountUseCase.Command(
                        UUID.randomUUID(),
                        concurrent.fixture().applicationId()
                ));
            });
            Future<SalaryAdvanceApplicationDto> submission = pool.submit(() -> {
                currentUser.customer(
                        concurrent.userId(),
                        concurrent.fixture().customerId()
                );
                start.await();
                return submit(concurrent.fixture().linkId());
            });
            start.countDown();

            assertEquals("CLOSED", closure.get(15, TimeUnit.SECONDS)
                    .resultingStatus().name());
            assertNotNull(submission.get(15, TimeUnit.SECONDS)
                    .loanApplicationId());
            assertEquals("CLOSED", status(concurrent.accountId()));
            assertEquals(applicationsBefore + 1,
                    customerApplications(concurrent));
            assertEquals(reservationsBefore + 1,
                    reservationMovements(concurrent));
            assertEquals(0, count(
                    "select count(*) from salary_advance_limit_movements "
                            + "where loan_application_id=? "
                            + "and movement_type='REPAID_RELEASED' "
                            + "and repayment_transaction_id is null",
                    concurrent.fixture().applicationId()
            ));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Activated activateAndPrepareCustomer() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        currentUser.staff();
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "SUBMISSION-GUARD-" + fixture.token()));
        jdbc.update("update salary_advance_limits set total_limit=1000000, "
                        + "available_amount=999000 where id=?", fixture.limitId());
        UUID userId = jdbc.queryForObject(
                "select id from users where customer_id=?", UUID.class, fixture.customerId());
        String unique = fixture.customerId().toString().replace("-", "");
        jdbc.update("update customers set profile_completion_status='COMPLETE' where id=?",
                fixture.customerId());
        jdbc.update("insert into customer_profiles "
                        + "(id,customer_id,full_name,identity_reference_ciphertext,"
                        + "identity_reference_fingerprint,identity_reference_last_four,phone_number,"
                        + "residential_address,employment_status,employer_name,"
                        + "terms_consent_accepted,data_processing_consent_accepted) "
                        + "values (?,?,'Outstanding Test Customer',?,?,'1234','0900000000',"
                        + "'Test Address','EMPLOYED','Test Employer',true,true)",
                UUID.randomUUID(), fixture.customerId(), "cipher-" + unique,
                "identity-fingerprint-" + unique);
        jdbc.update("insert into customer_partner_employee_links "
                        + "(id,customer_id,partner_company_id,partner_employee_id,source_import_batch_id,"
                        + "verification_outcome,link_status,verified_identity_ref,verified_employee_code,"
                        + "last_verified_at,last_refreshed_at) "
                        + "values (?,?,?,?,?,'MATCHED_ACTIVE','VERIFIED',?,'MER-EMP-001',?,?)",
                fixture.linkId(), fixture.customerId(), PARTNER_COMPANY_ID,
                PARTNER_EMPLOYEE_ID, IMPORT_BATCH_ID, "verified-" + unique,
                ManualDisbursementActivationPostgreSqlTestSupport.NOW,
                ManualDisbursementActivationPostgreSqlTestSupport.NOW);
        reset(limitRepository);
        return new Activated(fixture, activation.loanAccountId(), userId);
    }

    private RecordRepaymentUseCase.Result repayFully(Activated activated) {
        return repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), activated.fixture().applicationId(),
                "FINAL-" + UUID.randomUUID(), money("1100"), LocalDate.of(2026, 8, 29)));
    }

    private ApproveLoanSettlementUseCase.Result settleFully(Activated activated) {
        BigDecimal outstanding = jdbc.queryForObject(
                "select total_outstanding from loan_accounts where id=?",
                BigDecimal.class,
                activated.accountId()
        );
        return settlements.approve(new ApproveLoanSettlementUseCase.Command(
                UUID.randomUUID(),
                activated.fixture().applicationId(),
                outstanding,
                LocalDate.of(2026, 8, 29),
                "ADMIN-SETTLEMENT-" + UUID.randomUUID()
        ));
    }

    private SalaryAdvanceApplicationDto submit(UUID linkId) {
        return submissions.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(linkId, money("500000"), 1));
    }

    private BusinessStateConflictException conflictOnSubmit(UUID linkId) {
        try {
            submit(linkId);
            throw new AssertionError("Expected submission conflict");
        } catch (BusinessStateConflictException conflict) {
            return conflict;
        }
    }

    private void installCustomerLinkBarrier(
            Activated activated,
            String holdingThread,
            boolean holdAfterLock,
            CountDownLatch holdingThreadAtBoundary,
            CountDownLatch releaseHoldingThread,
            String waitingThread,
            CountDownLatch waitingThreadHasLock
    ) {
        doAnswer(invocation -> {
            UUID customerId = invocation.getArgument(0);
            UUID linkId = invocation.getArgument(1);
            boolean targetBoundary = activated.fixture().customerId().equals(customerId)
                    && activated.fixture().linkId().equals(linkId);
            String threadName = Thread.currentThread().getName();
            if (targetBoundary && holdingThread.equals(threadName) && !holdAfterLock) {
                holdingThreadAtBoundary.countDown();
                if (!releaseHoldingThread.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out before Customer/link lock.");
                }
            }
            invocation.callRealMethod();
            if (targetBoundary && holdingThread.equals(threadName) && holdAfterLock) {
                holdingThreadAtBoundary.countDown();
                if (!releaseHoldingThread.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out holding Customer/link lock.");
                }
            } else if (targetBoundary && waitingThread != null
                    && waitingThread.equals(threadName)) {
                waitingThreadHasLock.countDown();
            }
            return null;
        }).when(target(limitRepository)).acquireCustomerLinkLock(any(), any());
    }

    private void assertSafeConflict(
            BusinessStateConflictException conflict, Activated activated
    ) {
        String message = conflict.getMessage();
        assertFalse(message.contains(activated.accountId().toString()));
        assertFalse(message.contains(activated.fixture().customerId().toString()));
        assertFalse(message.contains(activated.fixture().linkId().toString()));
        assertFalse(message.contains(activated.fixture().limitId().toString()));
        assertFalse(message.contains("1100"));
    }

    private int customerApplications(Activated activated) {
        return count("select count(*) from loan_applications where customer_id=?",
                activated.fixture().customerId());
    }

    private int reservationMovements(Activated activated) {
        return count("select count(*) from salary_advance_limit_movements "
                        + "where salary_advance_limit_id=? and movement_type='RESERVED'",
                activated.fixture().limitId());
    }

    private int verificationSnapshots(Activated activated) {
        return count("select count(*) from salary_advance_verifications "
                        + "where customer_id=?", activated.fixture().customerId());
    }

    private int auditCount() {
        return count("select count(*) from audit_events");
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private static <T> T target(T proxiedSpy) {
        return AopTestUtils.getUltimateTargetObject(proxiedSpy);
    }

    private String status(UUID accountId) {
        return jdbc.queryForObject("select status from loan_accounts where id=?",
                String.class, accountId);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    private record Activated(
            ManualDisbursementActivationPostgreSqlTestSupport.Fixture fixture,
            UUID accountId,
            UUID userId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UserConfiguration {
        @Bean
        @Primary
        ThreadLocalCurrentUser threadLocalCurrentUser() {
            return new ThreadLocalCurrentUser();
        }
        @Bean
        @Primary
        Clock servicingClock() {
            return Clock.fixed(Instant.parse("2026-08-29T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    static class ThreadLocalCurrentUser implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> users = new ThreadLocal<>();

        void staff() {
            users.set(new AuthenticatedUser(
                    ACCOUNTING_USER_ID, "repayment.operator@meridian.test", "STAFF", null,
                    Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse", "repayment:update")));
        }

        void customer(UUID userId, UUID customerId) {
            users.set(new AuthenticatedUser(
                    userId, "customer@meridian.test", "CUSTOMER", customerId,
                    Set.of("CUSTOMER"), Set.of("loan:submit")));
        }

        void approver() {
            users.set(new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000303"),
                    "settlement.approver@meridian.test", "STAFF", null,
                    Set.of("APPROVER"), Set.of("loan:settlement:approve")));
        }

        @Override
        public AuthenticatedUser currentUser() {
            return users.get();
        }
    }
}

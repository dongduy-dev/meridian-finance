package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.domain.model.ContractSupersessionReason;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {MeridianPlatformApplication.class,
        LoanContractConcurrencyV25PostgreSqlIntegrationTest.TestConfig.class}, properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class LoanContractConcurrencyV25PostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_contract_races_v25_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACCOUNTING = UUID.fromString("00000000-0000-0000-0000-000000000304");

    @Autowired JdbcTemplate jdbc;
    @Autowired PrepareLoanContractUseCase prepare;
    @Autowired AcknowledgeLoanContractUseCase acknowledge;
    @Autowired LoanContractRepository contractRepository;
    @Autowired ConfirmContractReadinessUseCase confirm;
    @Autowired CustomerSensitiveValueProtector customerProtector;
    @Autowired LoanDocumentWorkflowPort documentWorkflow;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TestCurrentUserProvider currentUser;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void acknowledgmentAndRegenerationAreSerializedByTheWorkflowLock() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract first = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> acknowledgment = pool.submit(() -> {
                currentUser.set(customer(fixture));
                await(start);
                try {
                    return acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1));
                } catch (BusinessStateConflictException exception) {
                    return exception.getErrorCode();
                } finally {
                    currentUser.clear();
                }
            });
            Future<LoanContract> regeneration = pool.submit(() -> {
                currentUser.set(staff());
                await(start);
                try {
                    return prepare.prepare(new PrepareLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1,
                            ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH));
                } finally {
                    currentUser.clear();
                }
            });
            start.countDown();
            LoanContract second = regeneration.get(30, TimeUnit.SECONDS);
            Object acknowledgmentOutcome = acknowledgment.get(30, TimeUnit.SECONDS);
            assertEquals(2, second.contractVersion());
            assertTrue(acknowledgmentOutcome instanceof LoanContract
                    || "CONTRACT_VERSION_STALE".equals(acknowledgmentOutcome));
        }
        assertEquals("PREPARED", scalar(
                "select status from loan_contracts where loan_application_id = ? and contract_version = 2",
                fixture.applicationId()));
        assertEquals(1, count("select count(*) from loan_contracts "
                + "where loan_application_id = ? and status <> 'SUPERSEDED'", fixture.applicationId()));
    }

    @Test
    void acknowledgmentAndConfirmationRaceLeavesOneConsistentLifecycleOutcome() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<LoanContract> acknowledgment = pool.submit(() -> {
                currentUser.set(customer(fixture));
                await(start);
                try {
                    return acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1));
                } finally {
                    currentUser.clear();
                }
            });
            Future<Object> confirmation = pool.submit(() -> {
                currentUser.set(staff());
                await(start);
                try {
                    return confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1));
                } catch (BusinessStateConflictException exception) {
                    return exception.getErrorCode();
                } finally {
                    currentUser.clear();
                }
            });
            start.countDown();
            assertEquals(LoanContractStatus.ACKNOWLEDGED,
                    acknowledgment.get(30, TimeUnit.SECONDS).status());
            Object confirmationOutcome = confirmation.get(30, TimeUnit.SECONDS);
            assertTrue(confirmationOutcome instanceof LoanContract
                    || "ACKNOWLEDGMENT_MISSING".equals(confirmationOutcome));
        }
        String contractStatus = scalar("select status from loan_contracts where id = ?", prepared.id());
        assertTrue(contractStatus.equals("ACKNOWLEDGED")
                || contractStatus.equals("READY_FOR_DISBURSEMENT"));
        assertEquals(contractStatus.equals("READY_FOR_DISBURSEMENT")
                        ? "DISBURSEMENT_PENDING" : "CONTRACT_PENDING",
                scalar("select status from loan_applications where id = ?", fixture.applicationId()));
    }
    @Test
    void acknowledgmentWinsBeforeRegenerationWithNoPartialEffects() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract first = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> winner = workflowLockWinner(pool, fixture, customer(fixture), locked, release,
                    () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1)));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> peer = submitOutcome(pool, staff(), () -> prepare.prepare(
                    new PrepareLoanContractUseCase.Command(UUID.randomUUID(), fixture.applicationId(), 1,
                            ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)));
            assertThrows(TimeoutException.class, () -> peer.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(LoanContractStatus.ACKNOWLEDGED, ((LoanContract) winner.get(30, TimeUnit.SECONDS)).status());
            assertEquals(2, ((LoanContract) peer.get(30, TimeUnit.SECONDS)).contractVersion());
        }
        assertEquals(1, count("select count(*) from loan_contracts where id = ? and status = 'SUPERSEDED' "
                + "and acknowledgment_request_id is not null", first.id()));
        assertEquals("PREPARED", scalar("select status from loan_contracts where loan_application_id = ? "
                + "and contract_version = 2", fixture.applicationId()));
        assertRaceEffects(fixture, first, 1, 0, 0);
    }

    @Test
    void regenerationWinsBeforeAcknowledgmentAndMakesTheVersionStale() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract first = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> winner = workflowLockWinner(pool, fixture, staff(), locked, release,
                    () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1,
                            ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> peer = submitOutcome(pool, customer(fixture), () -> acknowledge.acknowledge(
                    new AcknowledgeLoanContractUseCase.Command(UUID.randomUUID(), fixture.applicationId(), 1)));
            assertThrows(TimeoutException.class, () -> peer.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(2, ((LoanContract) winner.get(30, TimeUnit.SECONDS)).contractVersion());
            assertEquals("CONTRACT_VERSION_STALE", peer.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, count("select count(*) from loan_contracts where id = ? and status = 'SUPERSEDED' "
                + "and acknowledgment_request_id is null", first.id()));
        assertEquals("PREPARED", scalar("select status from loan_contracts where loan_application_id = ? "
                + "and contract_version = 2", fixture.applicationId()));
        assertRaceEffects(fixture, first, 0, 0, 0);
    }

    @Test
    void acknowledgmentWinsBeforeConfirmationAndConfirmationThenCompletes() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> winner = workflowLockWinner(pool, fixture, customer(fixture), locked, release,
                    () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 1)));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> peer = submitOutcome(pool, staff(), () -> confirm.confirm(
                    new ConfirmContractReadinessUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1)));
            assertThrows(TimeoutException.class, () -> peer.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(LoanContractStatus.ACKNOWLEDGED, ((LoanContract) winner.get(30, TimeUnit.SECONDS)).status());
            assertEquals(LoanContractStatus.READY_FOR_DISBURSEMENT,
                    ((LoanContract) peer.get(30, TimeUnit.SECONDS)).status());
        }
        assertEquals(1, count("select count(*) from loan_contracts where id = ? and status = 'READY_FOR_DISBURSEMENT' "
                + "and acknowledgment_request_id is not null and confirmation_request_id is not null", prepared.id()));
        assertRaceEffects(fixture, prepared, 1, 1, 1);
    }

    @Test
    void confirmationAttemptWinsBeforeAcknowledgmentAndIsBlockedWithoutEffects() throws Exception {
        Fixture fixture = fixture();
        currentUser.set(staff());
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> winner = workflowLockWinner(pool, fixture, staff(), locked, release,
                    () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1)));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> peer = submitOutcome(pool, customer(fixture), () -> acknowledge.acknowledge(
                    new AcknowledgeLoanContractUseCase.Command(UUID.randomUUID(), fixture.applicationId(), 1)));
            assertThrows(TimeoutException.class, () -> peer.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("ACKNOWLEDGMENT_MISSING", winner.get(30, TimeUnit.SECONDS));
            assertEquals(LoanContractStatus.ACKNOWLEDGED, ((LoanContract) peer.get(30, TimeUnit.SECONDS)).status());
        }
        assertEquals(1, count("select count(*) from loan_contracts where id = ? and status = 'ACKNOWLEDGED' "
                + "and acknowledgment_request_id is not null and confirmation_request_id is null", prepared.id()));
        assertRaceEffects(fixture, prepared, 1, 0, 0);
    }


    @Test
    void identicalConcurrentRequestIdsReplayForEveryCommandCategory() throws Exception {
        Fixture fixture = fixture();
        UUID preparationRequest = UUID.randomUUID();
        RaceResults preparation = requestRace(staff(), staff(),
                () -> contractRepository.acquirePreparationRequestLock(preparationRequest),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                        preparationRequest, fixture.applicationId(), 0, null)),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                        preparationRequest, fixture.applicationId(), 0, null)));
        LoanContract prepared = (LoanContract) preparation.winner();
        assertEquals(prepared.id(), ((LoanContract) preparation.peer()).id());
        assertEquals(1, count("select count(*) from audit_events where entity_id = ? "
                + "and action = 'LOAN_CONTRACT_PREPARED'", prepared.id()));

        UUID acknowledgmentRequest = UUID.randomUUID();
        RaceResults acknowledgment = requestRace(customer(fixture), customer(fixture),
                () -> contractRepository.acquireAcknowledgmentRequestLock(acknowledgmentRequest),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, fixture.applicationId(), 1)),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, fixture.applicationId(), 1)));
        LoanContract acknowledged = (LoanContract) acknowledgment.winner();
        assertEquals(acknowledged.id(), ((LoanContract) acknowledgment.peer()).id());
        assertEquals(1, count("select count(*) from audit_events where entity_id = ? "
                + "and action = 'LOAN_CONTRACT_ACKNOWLEDGED'", acknowledged.id()));

        UUID confirmationRequest = UUID.randomUUID();
        RaceResults confirmation = requestRace(staff(), staff(),
                () -> contractRepository.acquireConfirmationRequestLock(confirmationRequest),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, fixture.applicationId(), acknowledged.id(), 1)),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, fixture.applicationId(), acknowledged.id(), 1)));
        LoanContract ready = (LoanContract) confirmation.winner();
        assertEquals(ready.id(), ((LoanContract) confirmation.peer()).id());
        assertRaceEffects(fixture, ready, 1, 1, 1);
    }

    @Test
    void concurrentRequestIdReuseAcrossApplicationsIsAConflictForEveryCategory() throws Exception {
        Fixture first = fixture();
        Fixture second = fixture();
        UUID preparationRequest = UUID.randomUUID();
        RaceResults preparation = requestRace(staff(), staff(),
                () -> contractRepository.acquirePreparationRequestLock(preparationRequest),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                        preparationRequest, first.applicationId(), 0, null)),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                        preparationRequest, second.applicationId(), 0, null)));
        LoanContract firstPrepared = (LoanContract) preparation.winner();
        assertEquals("IDEMPOTENCY_KEY_REUSED", preparation.peer());
        currentUser.set(staff());
        LoanContract secondPrepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), second.applicationId(), 0, null));

        UUID acknowledgmentRequest = UUID.randomUUID();
        RaceResults acknowledgment = requestRace(customer(first), customer(second),
                () -> contractRepository.acquireAcknowledgmentRequestLock(acknowledgmentRequest),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, first.applicationId(), 1)),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, second.applicationId(), 1)));
        LoanContract firstAcknowledged = (LoanContract) acknowledgment.winner();
        assertEquals("IDEMPOTENCY_KEY_REUSED", acknowledgment.peer());
        currentUser.set(customer(second));
        LoanContract secondAcknowledged = acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), second.applicationId(), 1));

        UUID confirmationRequest = UUID.randomUUID();
        RaceResults confirmation = requestRace(staff(), staff(),
                () -> contractRepository.acquireConfirmationRequestLock(confirmationRequest),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, first.applicationId(), firstAcknowledged.id(), 1)),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, second.applicationId(), secondAcknowledged.id(), 1)));
        assertTrue(confirmation.winner() instanceof LoanContract);
        assertEquals("IDEMPOTENCY_KEY_REUSED", confirmation.peer());
        assertEquals("ACKNOWLEDGED", scalar("select status from loan_contracts where id = ?", secondPrepared.id()));
        assertEquals(0, count("select count(*) from loan_application_status_transitions "
                + "where loan_application_id = ?", second.applicationId()));
        assertEquals(firstPrepared.id(), firstAcknowledged.id());
    }

    @Test
    void concurrentRequestIdReuseWithDifferentVersionOrContentIsAConflictForEveryCategory() throws Exception {
        Fixture preparationFixture = fixture();
        currentUser.set(staff());
        prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), preparationFixture.applicationId(), 0, null));
        UUID preparationRequest = UUID.randomUUID();
        RaceResults preparation = requestRace(staff(), staff(),
                () -> contractRepository.acquirePreparationRequestLock(preparationRequest),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(preparationRequest,
                        preparationFixture.applicationId(), 1, ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)),
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(preparationRequest,
                        preparationFixture.applicationId(), 0, null)));
        assertEquals(2, ((LoanContract) preparation.winner()).contractVersion());
        assertEquals("IDEMPOTENCY_KEY_REUSED", preparation.peer());

        Fixture acknowledgmentFixture = fixture();
        currentUser.set(staff());
        prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), acknowledgmentFixture.applicationId(), 0, null));
        UUID acknowledgmentRequest = UUID.randomUUID();
        RaceResults acknowledgment = requestRace(customer(acknowledgmentFixture), customer(acknowledgmentFixture),
                () -> contractRepository.acquireAcknowledgmentRequestLock(acknowledgmentRequest),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, acknowledgmentFixture.applicationId(), 1)),
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        acknowledgmentRequest, acknowledgmentFixture.applicationId(), 2)));
        assertTrue(acknowledgment.winner() instanceof LoanContract);
        assertEquals("IDEMPOTENCY_KEY_REUSED", acknowledgment.peer());

        Fixture confirmationFixture = fixture();
        LoanContract acknowledged = prepareAndAcknowledge(confirmationFixture);
        UUID confirmationRequest = UUID.randomUUID();
        RaceResults confirmation = requestRace(staff(), staff(),
                () -> contractRepository.acquireConfirmationRequestLock(confirmationRequest),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, confirmationFixture.applicationId(), acknowledged.id(), 1)),
                () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                        confirmationRequest, confirmationFixture.applicationId(), acknowledged.id(), 2)));
        assertTrue(confirmation.winner() instanceof LoanContract);
        assertEquals("IDEMPOTENCY_KEY_REUSED", confirmation.peer());
    }

    @Test
    void reservationReleaseCompletesBeforeConfirmationAndBlocksIt() throws Exception {
        Fixture fixture = fixture();
        LoanContract acknowledged = prepareAndAcknowledge(fixture);
        UUID limitId = jdbc.queryForObject("select salary_advance_limit_id from salary_advance_verifications "
                + "where loan_application_id = ?", UUID.class, fixture.applicationId());
        UUID linkId = jdbc.queryForObject("select customer_partner_employee_link_id from salary_advance_verifications "
                + "where loan_application_id = ?", UUID.class, fixture.applicationId());
        CountDownLatch limitLocked = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> mutation = pool.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    String key = "salary-advance-limit:" + fixture.customerId() + ":" + linkId;
                    jdbc.queryForObject("WITH lock AS (SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(? AS text), 0))) SELECT 1 FROM lock", Integer.class, key);
                    jdbc.queryForObject("select id from salary_advance_limits where id = ? for update",
                            UUID.class, limitId);
                    jdbc.update("update salary_advance_limits set reserved_amount = 0, available_amount = total_limit "
                            + "- used_amount where id = ?", limitId);
                    jdbc.update("insert into salary_advance_limit_movements "
                                    + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                                    + "values (?,?,?,'RESERVATION_RELEASED',1000,current_timestamp)",
                            UUID.randomUUID(), limitId, fixture.applicationId());
                    limitLocked.countDown();
                    await(releaseMutation);
                });
                return null;
            });
            assertTrue(limitLocked.await(10, TimeUnit.SECONDS));
            Future<Object> confirmation = submitOutcome(pool, staff(), () -> confirm.confirm(
                    new ConfirmContractReadinessUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), acknowledged.id(), 1)));
            assertThrows(TimeoutException.class, () -> confirmation.get(250, TimeUnit.MILLISECONDS));
            releaseMutation.countDown();
            mutation.get(30, TimeUnit.SECONDS);
            assertEquals("SALARY_ADVANCE_RESERVATION_RELEASED", confirmation.get(30, TimeUnit.SECONDS));
        }
        assertEquals("ACKNOWLEDGED", scalar("select status from loan_contracts where id = ?", acknowledged.id()));
        assertEquals(1, count("select count(*) from salary_advance_limit_movements where loan_application_id = ? "
                + "and movement_type = 'RESERVATION_RELEASED'", fixture.applicationId()));
        assertRaceEffects(fixture, acknowledged, 1, 0, 0);
    }

    @Test
    void primaryAccountChangeCompletesBeforePreparationCapturesTheLockedState() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch customerLocked = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> mutation = pool.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject("select id from customers where id = ? for update",
                            UUID.class, fixture.customerId());
                    jdbc.update("update customer_bank_accounts set primary_account = false where id = ?",
                            fixture.bankAccountId());
                    jdbc.update("update customer_bank_accounts set primary_account = true where id = ?",
                            fixture.alternateBankAccountId());
                    customerLocked.countDown();
                    await(releaseMutation);
                });
                return null;
            });
            assertTrue(customerLocked.await(10, TimeUnit.SECONDS));
            Future<LoanContract> preparation = pool.submit(() -> {
                currentUser.set(staff());
                try {
                    return prepare.prepare(new PrepareLoanContractUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), 0, null));
                } finally {
                    currentUser.clear();
                }
            });
            assertThrows(TimeoutException.class, () -> preparation.get(250, TimeUnit.MILLISECONDS));
            releaseMutation.countDown();
            mutation.get(30, TimeUnit.SECONDS);
            assertEquals(fixture.alternateBankAccountId(),
                    preparation.get(30, TimeUnit.SECONDS).disbursementBankAccount().sourceBankAccountId());
        }
    }

    @Test
    void capturedAccountDeactivationCompletesBeforeConfirmationAndBlocksIt() throws Exception {
        Fixture fixture = fixture();
        LoanContract acknowledged = prepareAndAcknowledge(fixture);
        CountDownLatch customerLocked = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> mutation = pool.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject("select id from customers where id = ? for update",
                            UUID.class, fixture.customerId());
                    jdbc.update("update customer_bank_accounts set status = 'DEACTIVATED', "
                                    + "primary_account = false, deactivated_at = current_timestamp where id = ?",
                            fixture.bankAccountId());
                    customerLocked.countDown();
                    await(releaseMutation);
                });
                return null;
            });
            assertTrue(customerLocked.await(10, TimeUnit.SECONDS));
            Future<Object> confirmation = pool.submit(() -> confirmOutcome(fixture, acknowledged));
            assertThrows(TimeoutException.class, () -> confirmation.get(250, TimeUnit.MILLISECONDS));
            releaseMutation.countDown();
            mutation.get(30, TimeUnit.SECONDS);
            assertEquals("CAPTURED_ACCOUNT_INACTIVE", confirmation.get(30, TimeUnit.SECONDS));
        }
        assertEquals("ACKNOWLEDGED", scalar("select status from loan_contracts where id = ?", acknowledged.id()));
        assertEquals("CONTRACT_PENDING", scalar(
                "select status from loan_applications where id = ?", fixture.applicationId()));
    }

    @Test
    void documentReadinessMutationCompletesBeforeConfirmationAndBlocksIt() throws Exception {
        Fixture fixture = fixture();
        LoanContract acknowledged = prepareAndAcknowledge(fixture);
        UUID checklistId = jdbc.queryForObject(
                "select id from document_checklists where loan_application_id = ? and stage = 'SUBMISSION'",
                UUID.class, fixture.applicationId());
        CountDownLatch workflowLocked = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> mutation = pool.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    documentWorkflow.lock(fixture.applicationId());
                    jdbc.update("insert into document_checklist_items "
                                    + "(id, checklist_id, document_type, requirement_status, created_at, updated_at) "
                                    + "values (?, ?, 'RECENT_PAYSLIP', 'REQUIRED', current_timestamp, current_timestamp)",
                            UUID.randomUUID(), checklistId);
                    workflowLocked.countDown();
                    await(releaseMutation);
                });
                return null;
            });
            assertTrue(workflowLocked.await(10, TimeUnit.SECONDS));
            Future<Object> confirmation = pool.submit(() -> confirmOutcome(fixture, acknowledged));
            assertThrows(TimeoutException.class, () -> confirmation.get(250, TimeUnit.MILLISECONDS));
            releaseMutation.countDown();
            mutation.get(30, TimeUnit.SECONDS);
            assertEquals("DOCUMENTS_NOT_PROCESSING_READY", confirmation.get(30, TimeUnit.SECONDS));
        }
        assertEquals("ACKNOWLEDGED", scalar("select status from loan_contracts where id = ?", acknowledged.id()));
    }

    @Test
    void simultaneousDuplicateConfirmationReplaysOnePersistedResult() throws Exception {
        Fixture fixture = fixture();
        LoanContract acknowledged = prepareAndAcknowledge(fixture);
        UUID requestId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<LoanContract> action = () -> {
                currentUser.set(staff());
                await(start);
                try {
                    return confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                            requestId, fixture.applicationId(), acknowledged.id(), 1));
                } finally {
                    currentUser.clear();
                }
            };
            Future<LoanContract> first = pool.submit(action);
            Future<LoanContract> second = pool.submit(action);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS).id(), second.get(30, TimeUnit.SECONDS).id());
        }
        assertEquals(1, count("select count(*) from loan_application_status_transitions "
                + "where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'",
                fixture.applicationId()));
        assertEquals(1, count("select count(*) from audit_events "
                + "where entity_id = ? and action = 'LOAN_CONTRACT_READINESS_CONFIRMED'", acknowledged.id()));
    }

    @Test
    void migrationInstallsCompositeOwnershipConstraints() {
        assertEquals(1, count("select count(*) from pg_constraint "
                + "where connamespace = ?::regnamespace and conname = 'fk_loan_contracts_application_customer'", SCHEMA));
        assertEquals(1, count("select count(*) from pg_constraint "
                + "where connamespace = ?::regnamespace and conname = 'fk_loan_contracts_source_account_customer'", SCHEMA));
        assertEquals(1, count("select count(*) from pg_constraint "
                + "where connamespace = ?::regnamespace and conname = 'fk_loan_contracts_offer_application'", SCHEMA));
    }

    private Object confirmOutcome(Fixture fixture, LoanContract contract) {
        currentUser.set(staff());
        try {
            return confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                    UUID.randomUUID(), fixture.applicationId(), contract.id(), contract.contractVersion()));
        } catch (BusinessStateConflictException exception) {
            return exception.getErrorCode();
        } finally {
            currentUser.clear();
        }
    }

    private LoanContract prepareAndAcknowledge(Fixture fixture) {
        currentUser.set(staff());
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 0, null));
        currentUser.set(customer(fixture));
        return acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 1));
    }
    private Future<Object> workflowLockWinner(
            ExecutorService pool, Fixture fixture, AuthenticatedUser actor,
            CountDownLatch locked, CountDownLatch release, Supplier<Object> action
    ) {
        return pool.submit(() -> {
            currentUser.set(actor);
            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    acquireWorkflowLock(fixture.applicationId());
                    locked.countDown();
                    await(release);
                    return action.get();
                });
            } catch (BusinessStateConflictException exception) {
                return exception.getErrorCode();
            } finally {
                currentUser.clear();
            }
        });
    }

    private RaceResults requestRace(
            AuthenticatedUser winnerActor, AuthenticatedUser peerActor, Runnable acquireRequestLock,
            Supplier<Object> winnerAction, Supplier<Object> peerAction
    ) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> winner = requestLockWinner(
                    pool, winnerActor, acquireRequestLock, locked, release, winnerAction);
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> peer = submitOutcome(pool, peerActor, peerAction);
            assertThrows(TimeoutException.class, () -> peer.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            return new RaceResults(winner.get(30, TimeUnit.SECONDS), peer.get(30, TimeUnit.SECONDS));
        }
    }

    private Future<Object> requestLockWinner(
            ExecutorService pool, AuthenticatedUser actor, Runnable acquireRequestLock,
            CountDownLatch locked, CountDownLatch release, Supplier<Object> action
    ) {
        return pool.submit(() -> {
            currentUser.set(actor);
            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    acquireRequestLock.run();
                    locked.countDown();
                    await(release);
                    return action.get();
                });
            } finally {
                currentUser.clear();
            }
        });
    }

    private Future<Object> submitOutcome(
            ExecutorService pool, AuthenticatedUser actor, Supplier<Object> action
    ) {
        return pool.submit(() -> {
            currentUser.set(actor);
            try {
                return action.get();
            } catch (BusinessStateConflictException exception) {
                return exception.getErrorCode();
            } finally {
                currentUser.clear();
            }
        });
    }

    private void acquireWorkflowLock(UUID applicationId) {
        String key = "loan-application:workflow:" + applicationId;
        jdbc.queryForObject("WITH lock AS (SELECT pg_advisory_xact_lock("
                + "hashtextextended(CAST(? AS text), 0))) SELECT 1 FROM lock", Integer.class, key);
    }

    private void assertRaceEffects(Fixture fixture, LoanContract contract,
                                   int acknowledgmentAudits, int confirmationAudits, int historyRows) {
        assertEquals(acknowledgmentAudits, count("select count(*) from audit_events where entity_id = ? "
                + "and action = 'LOAN_CONTRACT_ACKNOWLEDGED'", contract.id()));
        assertEquals(confirmationAudits, count("select count(*) from audit_events where entity_id = ? "
                + "and action = 'LOAN_CONTRACT_READINESS_CONFIRMED'", contract.id()));
        assertEquals(historyRows, count("select count(*) from loan_application_status_transitions where "
                + "loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'", fixture.applicationId()));
        assertEquals(historyRows == 1 ? "DISBURSEMENT_PENDING" : "CONTRACT_PENDING",
                scalar("select status from loan_applications where id = ?", fixture.applicationId()));
    }


    private Fixture fixture() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID alternateBankAccountId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID approvedOfferId = UUID.randomUUID();
        UUID approvedOfferItemId = UUID.randomUUID();
        UUID employeeLinkId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        String token = customerId.toString().replace("-", "");

        jdbc.update("insert into customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE')",
                customerId, "CUS-R-" + token.substring(0, 12));
        jdbc.update("insert into users "
                        + "(id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) "
                        + "values (?,?,?,'not-used','CUSTOMER','ACTIVE','Contract Customer',?)",
                customerUserId, "race-" + token + "@meridian.test",
                "race-" + token + "@meridian.test", customerId);
        ProtectedSensitiveValue bank = customerProtector.protectBankAccountNumber("VCB", "1234567890");
        jdbc.update("insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,status,primary_account) "
                        + "values (?,?, 'VCB','Vietcombank','MERIDIAN CUSTOMER',?,?,?,'ACTIVE',TRUE)",
                bankAccountId, customerId, bank.ciphertext(), bank.fingerprint(), bank.lastFour());
        ProtectedSensitiveValue alternate = customerProtector.protectBankAccountNumber("ACB", "9999994321");
        jdbc.update("insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,status,primary_account) "
                        + "values (?,?, 'ACB','Asia Commercial Bank','MERIDIAN CUSTOMER',?,?,?,'ACTIVE',FALSE)",
                alternateBankAccountId, customerId, alternate.ciphertext(), alternate.fingerprint(), alternate.lastFour());

        UUID productId = jdbc.queryForObject(
                "select id from loan_products where product_code='SALARY_ADVANCE'", UUID.class);
        UUID policyId = jdbc.queryForObject("select id from loan_product_policies "
                + "where loan_product_id=? and policy_code='DEFAULT_POLICY'", UUID.class, productId);
        jdbc.update("insert into loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,product_type,status,"
                        + "requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','CONTRACT_PENDING',1000,1,current_timestamp)",
                applicationId, customerId, productId, "SA-R-" + token.substring(0, 16));
        jdbc.update("insert into approved_offers "
                        + "(id,loan_application_id,source_loan_product_policy_id,status,approved_principal,"
                        + "approved_term_months,interest_calculation_method,flat_monthly_interest_rate,total_interest,"
                        + "fee_amount,total_repayment_amount,repayment_method,generated_at,expires_at,accepted_at) "
                        + "values (?,?,?,'ACCEPTED',1000,1,'FLAT_ORIGINAL_PRINCIPAL',0.1,100,0,1100,"
                        + "'ON_SALARY_DATE',current_timestamp-interval '2 day',"
                        + "current_timestamp-interval '1 day',current_timestamp)",
                approvedOfferId, applicationId, policyId);
        jdbc.update("insert into approved_offer_repayment_items "
                        + "(id,approved_offer_id,installment_number,principal_due,interest_due,fee_due,total_due) "
                        + "values (?,?,1,1000,100,0,1100)", approvedOfferItemId, approvedOfferId);
        jdbc.update("insert into document_checklists (id,loan_application_id,stage,created_at) "
                        + "values (?,?, 'SUBMISSION',current_timestamp)",
                UUID.randomUUID(), applicationId);
        jdbc.update("insert into salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,total_limit,used_amount,reserved_amount,"
                        + "available_amount,status,last_refreshed_at) "
                        + "values (?,?,?,2000,0,1000,1000,'ACTIVE',current_timestamp)",
                limitId, customerId, employeeLinkId);
        jdbc.update("insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                        + "values (?,?,?,'RESERVED',1000,current_timestamp)",
                UUID.randomUUID(), limitId, applicationId);
        jdbc.update("insert into salary_advance_verifications "
                        + "(id,loan_application_id,verification_sequence,customer_id,customer_partner_employee_link_id,"
                        + "salary_advance_limit_id,partner_company_id,partner_employee_id,source_import_batch_id,"
                        + "employee_verification_outcome,product_verification_result,total_limit_snapshot,"
                        + "used_amount_snapshot,reserved_amount_snapshot,available_limit_snapshot,verified_at) "
                        + "values (?,?,1,?,?,?,?,?,?, 'MATCHED_ACTIVE','VERIFIED',2000,0,1000,1000,current_timestamp)",
                UUID.randomUUID(), applicationId, customerId, employeeLinkId, limitId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return new Fixture(customerId, customerUserId, applicationId, bankAccountId, alternateBankAccountId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String scalar(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test coordination was interrupted.", exception);
        }
    }

    private static AuthenticatedUser staff() {
        return new AuthenticatedUser(ACCOUNTING, "accounting.officer@meridian.local",
                "STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of());
    }

    private static AuthenticatedUser customer(Fixture fixture) {
        return new AuthenticatedUser(fixture.customerUserId(), "customer@meridian.test",
                "CUSTOMER", fixture.customerId(), Set.of("CUSTOMER"), Set.of());
    }

    private record Fixture(UUID customerId, UUID customerUserId, UUID applicationId,
                           UUID bankAccountId, UUID alternateBankAccountId) {
    }

    private record RaceResults(Object winner, Object peer) {}

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        TestCurrentUserProvider testCurrentUserProvider() {
            return new TestCurrentUserProvider();

        }
    }

    static final class TestCurrentUserProvider implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> value = new ThreadLocal<>();

        void set(AuthenticatedUser user) {
            value.set(user);
        }

        void clear() {
            value.remove();
        }

        @Override
        public AuthenticatedUser currentUser() {
            return Objects.requireNonNull(value.get(), "Test user is required.");
        }
    }
}

package com.meridian.platform.document;

import com.meridian.platform.document.application.spi.loan.LoanDocumentWorkflowSpi;
import com.meridian.platform.document.application.spi.identity.DocumentStaffActorDirectorySpi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
class LoanWorkflowSpiPostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_workflow_spi_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired LoanDocumentWorkflowSpi workflows;
    @Autowired DocumentStaffActorDirectorySpi staffActors;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void findTakesNeitherWorkflowAdvisoryNorApplicationRowLock() throws Exception {
        UUID id = insertCommittedApplication();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var snapshot = workflows.find(id);
                assertEquals(id, snapshot.loanApplicationId());
                assertEquals("SUBMITTED", snapshot.status());
                try (Connection observer = dataSource.getConnection()) {
                    observer.setAutoCommit(false);
                    assertTrue(tryWorkflowLock(observer, id));
                    // A second transaction can lock the row while the caller read transaction is still open.
                    lockRow(observer, id);
                    // MVCC read also succeeds with the other transaction's row lock held.
                    assertEquals(snapshot, workflows.find(id));
                    observer.rollback();
                } catch (SQLException exception) { throw new AssertionError(exception); }
            });
        } finally { deleteApplication(id); }
    }

    @Test
    void lockWaitsForAdvisoryBeforeTakingRowAndHoldsBothUntilCallerTransactionEnds() throws Exception {
        UUID id = insertCommittedApplication();
        CountDownLatch started = new CountDownLatch(1), locked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger workerPid = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor(); Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            assertTrue(tryWorkflowLock(blocker, id));
            var future = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                workerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                started.countDown();
                var result = workflows.lock(id);
                locked.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                return result;
            }));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                    while (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid = ? AND locktype = 'advisory' AND NOT granted",
                            Integer.class, workerPid.get()) == 0) Thread.sleep(20);
                });
                assertEquals(1, locked.getCount());
                // The waiting worker has not taken the aggregate row lock before its advisory lock.
                lockRow(blocker, id);
                blocker.rollback();
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                assertFalse(tryWorkflowLock(blocker, id));
                SQLException rowLocked = assertThrows(SQLException.class, () -> lockRow(blocker, id));
                assertEquals("55P03", rowLocked.getSQLState());
                blocker.rollback();
            } finally {
                blocker.rollback();
                release.countDown();
            }
            assertEquals(id, future.get(5, TimeUnit.SECONDS).loanApplicationId());
            assertTrue(tryWorkflowLock(blocker, id));
            lockRow(blocker, id);
            blocker.rollback();
        } finally { release.countDown(); deleteApplication(id); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothOperationsSeeCallerUncommittedStateAndLateMandatoryFailureRollsItBack(boolean locking) {
        UUID id = UUID.randomUUID();
        var failure = new IllegalStateException("late mandatory caller failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    insertApplication(id);
                    var snapshot = locking ? workflows.lock(id) : workflows.find(id);
                    assertEquals(id, snapshot.loanApplicationId());
                    assertEquals("CUSTOMER_DIGITAL", snapshot.originationChannel());
                    throw failure;
                })));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE id = ?", Integer.class, id));
    }

    @Test
    void identityDirectoryReturnsOnlyRequestedStaffAndImmutableFacts() {
        UUID staff = jdbc.queryForObject("SELECT id FROM users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1", UUID.class);
        UUID customer = jdbc.queryForObject("SELECT id FROM users WHERE user_type = 'CUSTOMER' ORDER BY id LIMIT 1", UUID.class);
        var summaries = staffActors.findByUserIds(Set.of(staff, customer, UUID.randomUUID()));
        assertEquals(Set.of(staff), summaries.keySet());
        assertEquals(staff, summaries.get(staff).userId());
        assertNotNull(summaries.get(staff).displayName());
        assertNotNull(summaries.get(staff).email());
        assertThrows(UnsupportedOperationException.class, summaries::clear);
        assertTrue(staffActors.findByUserIds(Set.of()).isEmpty());
        assertThrows(NullPointerException.class, () -> staffActors.findByUserIds(null));
    }

    private UUID insertCommittedApplication() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> insertApplication(id));
        return id;
    }

    private void insertApplication(UUID id) {
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM users WHERE user_type = 'CUSTOMER' ORDER BY id LIMIT 1", UUID.class);
        UUID product = jdbc.queryForObject("SELECT id FROM loan_products WHERE product_code = 'SALARY_ADVANCE'", UUID.class);
        jdbc.update("""
                INSERT INTO loan_applications (id, customer_id, loan_product_id, application_number,
                    product_code, product_type, status, requested_amount, requested_term_months, submitted_at)
                VALUES (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'SUBMITTED', 3000000, 1, now())
                """, id, customer, product, "SA-SPI-" + id);
    }

    private void deleteApplication(UUID id) { jdbc.update("DELETE FROM loan_applications WHERE id = ?", id); }

    private static boolean tryWorkflowLock(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))")) {
            statement.setString(1, "loan-application:workflow:" + id);
            try (var result = statement.executeQuery()) { assertTrue(result.next()); return result.getBoolean(1); }
        }
    }

    private static void lockRow(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT id FROM loan_applications WHERE id = ? FOR UPDATE NOWAIT")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) { assertTrue(result.next()); }
        }
    }
}

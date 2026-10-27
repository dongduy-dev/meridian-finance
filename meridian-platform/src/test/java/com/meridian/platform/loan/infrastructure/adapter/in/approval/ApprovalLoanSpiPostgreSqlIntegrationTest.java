package com.meridian.platform.loan.infrastructure.adapter.in.approval;

import com.meridian.platform.approval.application.spi.loan.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"})
class ApprovalLoanSpiPostgreSqlIntegrationTest {
    private static final String SCHEMA = "approval_spi_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID OFFICER = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 1, 8, 0);
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApprovalLoanCaseSpi cases;
    @Autowired ApprovalLoanReviewCycleSpi cycles;
    @Autowired ApprovalLoanReviewHistorySpi history;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void clearApplicationsInTheIsolatedSchema() { jdbc.execute("TRUNCATE loan_applications CASCADE"); }

    @Test
    void allThreeSpisReadUncommittedCallerFactsAndShareItsRepeatableReadRollback() {
        UUID id = UUID.randomUUID();
        var transaction = transaction();
        transaction.executeWithoutResult(status -> {
            seed(id, "COLLATERAL_LOAN", "APPROVAL_PENDING", TIME);
            var tx = jdbc.queryForObject("select txid_current()", Long.class);
            assertEquals("repeatable read", jdbc.queryForObject("show transaction_isolation", String.class));
            assertEquals(id, cases.findCase(id).orElseThrow().loanApplicationId());
            assertEquals("VERIFIED", cases.findCase(id).orElseThrow().productReadiness().productVerificationResult());
            assertEquals(OFFICER, cycles.findActiveReviewCycle(id).orElseThrow().assignedLoanOfficerUserId());
            assertEquals(1, history.findHistory(id).orElseThrow().cycles().size());
            assertEquals(List.of(id), cases.findDecisionQueue(null, 0, 10).items().stream().map(i -> i.loanApplicationId()).toList());
            assertEquals(tx, jdbc.queryForObject("select txid_current()", Long.class));
            status.setRollbackOnly();
        });
        assertTrue(cases.findCase(id).isEmpty());
        assertTrue(cycles.findActiveReviewCycle(id).isEmpty());
        assertTrue(history.findHistory(id).isEmpty());
        assertEquals(0, cases.findDecisionQueue(null, 0, 10).totalElements());
    }

    @Test
    void caseCycleAndHistoryRemainNonLockingWhileAnotherConnectionHoldsWorkflowAndRowLocks() throws Exception {
        UUID id = UUID.randomUUID();
        seed(id, "COLLATERAL_LOAN", "APPROVAL_PENDING", TIME);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var lock = blocker.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    lock.setString(1, "loan-application:workflow:" + id);
                    lock.execute();
                }
                try (var lock = blocker.prepareStatement("select id from " + SCHEMA + ".loan_applications where id=? for update")) {
                    lock.setObject(1, id);
                    lock.execute();
                }
                try (var lock = blocker.prepareStatement("select id from " + SCHEMA + ".loan_application_review_cycles where loan_application_id=? for update")) {
                    lock.setObject(1, id);
                    lock.execute();
                }
                transaction().executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='250ms'");
                    assertNotNull(cases.findCase(id).orElseThrow());
                    assertNotNull(cycles.findActiveReviewCycle(id).orElseThrow());
                    assertEquals(1, history.findHistory(id).orElseThrow().cycles().size());
                    assertEquals(0, jdbc.queryForObject("select count(*) from pg_locks where pid=pg_backend_pid() "
                            + "and (locktype='advisory' or mode='RowShareLock')", Integer.class));
                });
            } finally { blocker.rollback(); }
        }
    }

    @Test
    void realQueueFiltersPendingAndProductAndPreservesCountsPageBoundariesAndTieOrdering() {
        transaction().executeWithoutResult(status -> {
            UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
            UUID second = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
            UUID third = UUID.randomUUID();
            seed(second, "COLLATERAL_LOAN", "APPROVAL_PENDING", TIME.plusHours(1));
            seed(first, "COLLATERAL_LOAN", "APPROVAL_PENDING", TIME.plusHours(1));
            seed(third, "UNSECURED_CONSUMER_LOAN", "APPROVAL_PENDING", TIME);
            seed(UUID.randomUUID(), "SALARY_ADVANCE", "UNDER_REVIEW", TIME.plusDays(1));
            var all = cases.findDecisionQueue(null, 0, 2);
            assertEquals(3, all.totalElements());
            assertEquals(2, all.totalPages());
            assertEquals(List.of(first, second), all.items().stream().map(i -> i.loanApplicationId()).toList());
            assertTrue(all.items().stream().allMatch(i -> i.applicationStatus().equals("APPROVAL_PENDING")));
            assertEquals(List.of(third), cases.findDecisionQueue(null, 1, 2).items().stream().map(i -> i.loanApplicationId()).toList());
            var selected = cases.findDecisionQueue("COLLATERAL_LOAN", 1, 1);
            assertEquals(1, selected.page());
            assertEquals(1, selected.size());
            assertEquals(2, selected.totalElements());
            assertEquals(2, selected.totalPages());
            assertEquals(second, selected.items().getFirst().loanApplicationId());
            var outside = cases.findDecisionQueue("COLLATERAL_LOAN", 2, 1);
            assertEquals(2, outside.totalElements());
            assertTrue(outside.items().isEmpty());
            assertTrue(cases.findDecisionQueue("SALARY_ADVANCE", 0, 10).items().isEmpty());
            status.setRollbackOnly();
        });
    }

    private TransactionTemplate transaction() {
        var result = new TransactionTemplate(transactions);
        result.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return result;
    }

    private void seed(UUID application, String product, String state, LocalDateTime time) {
        UUID customer = UUID.randomUUID();
        jdbc.update("insert into customers (id,customer_number,status,verification_status,profile_completion_status) "
                + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')", customer, "SPI-" + customer);
        String type = switch (product) {
            case "SALARY_ADVANCE" -> "SALARY_BASED";
            case "UNSECURED_CONSUMER_LOAN" -> "UNSECURED";
            default -> "SECURED";
        };
        jdbc.update("insert into loan_applications (id,customer_id,loan_product_id,application_number,product_code,"
                + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                + "values (?,?,(select id from loan_products where product_code=?),?,?,?,?,5000000,6,?)",
                application, customer, product, "SPI-" + application, product, type, state, time);
        jdbc.update("insert into loan_application_review_cycles (id,loan_application_id,cycle_number,"
                + "assigned_loan_officer_user_id,status,started_at) values (?,?,1,?,'ACTIVE',?)",
                UUID.randomUUID(), application, OFFICER, time);
        if (product.equals("COLLATERAL_LOAN")) {
            jdbc.update("insert into document_checklists (id,loan_application_id,stage,created_at) values (?,?,'SUBMISSION',?)",
                    UUID.randomUUID(), application, time);
            jdbc.update("insert into collateral_loan_verifications (id,loan_application_id,verification_sequence,"
                    + "product_verification_result,created_at,reviewed_by_user_id,reviewed_at,assessment_note) "
                    + "values (?,?,1,'VERIFIED',?,?,?,'Synthetic assessment')",
                    UUID.randomUUID(), application, time, OFFICER, time);
        }
    }
}

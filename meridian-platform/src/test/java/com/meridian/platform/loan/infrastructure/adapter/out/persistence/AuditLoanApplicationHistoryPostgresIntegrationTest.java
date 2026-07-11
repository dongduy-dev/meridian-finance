package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.domain.model.ActionActor;

@SpringBootTest
class AuditLoanApplicationHistoryPostgresIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private LoanApplicationStatusTransitionRepository transitionRepository;

    @Test
    void acceptsValidUserAndSystemHistoryAndAuditRows() {
        UUID loanApplicationId = insertLoanApplication();

        assertEquals(1, insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                null,
                "SUBMITTED",
                "APPLICATION_SUBMITTED",
                "USER",
                USER_ID
        ));
        assertEquals(1, insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                "CUSTOMER_ACCEPTANCE_PENDING",
                "EXPIRED",
                "OFFER_EXPIRED",
                "SYSTEM",
                null
        ));
        assertEquals(1, insertAudit(
                UUID.randomUUID(),
                (short) 1,
                "USER",
                USER_ID,
                "LOAN_APPLICATION",
                loanApplicationId,
                "APPLICATION_SUBMITTED",
                "{}"
        ));
        assertEquals(1, insertAudit(
                UUID.randomUUID(),
                (short) 1,
                "SYSTEM",
                null,
                "LOAN_APPLICATION",
                loanApplicationId,
                "OFFER_EXPIRED",
                "{\"productCode\":\"SALARY_ADVANCE\"}"
        ));
    }

    @Test
    void rejectsInvalidHistoryRowsAndAppendOnlyMutation() {
        UUID loanApplicationId = insertLoanApplication();

        assertDatabaseRejects(() -> insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                "SUBMITTED",
                "UNDER_REVIEW",
                "REVIEW_STARTED",
                "USER",
                null
        ));
        assertDatabaseRejects(() -> insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                "CUSTOMER_ACCEPTANCE_PENDING",
                "EXPIRED",
                "OFFER_EXPIRED",
                "SYSTEM",
                USER_ID
        ));
        assertDatabaseRejects(() -> insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                null,
                "UNDER_REVIEW",
                "REVIEW_STARTED",
                "USER",
                USER_ID
        ));
        assertDatabaseRejects(() -> insertHistory(
                UUID.randomUUID(),
                (short) 1,
                loanApplicationId,
                "SUBMITTED",
                "NOT_A_STATUS",
                "REVIEW_STARTED",
                "USER",
                USER_ID
        ));

        UUID operationId = UUID.randomUUID();
        insertHistory(operationId, (short) 1, loanApplicationId, "SUBMITTED", "UNDER_REVIEW", "REVIEW_STARTED", "USER", USER_ID);
        assertDatabaseRejects(() -> insertHistory(operationId, (short) 1, loanApplicationId, "UNDER_REVIEW", "APPROVAL_PENDING", "RECOMMEND_APPROVAL", "USER", USER_ID));

        UUID transitionId = UUID.randomUUID();
        insertHistoryWithId(transitionId, UUID.randomUUID(), loanApplicationId);
        assertDatabaseRejects(() -> jdbcTemplate.update(
                "UPDATE loan_application_status_transitions SET action = action WHERE id = ?",
                transitionId
        ));
        assertDatabaseRejects(() -> jdbcTemplate.update(
                "DELETE FROM loan_application_status_transitions WHERE id = ?",
                transitionId
        ));
    }

    @Test
    void rejectsInvalidAuditRowsAndAppendOnlyMutation() {
        UUID loanApplicationId = insertLoanApplication();

        assertDatabaseRejects(() -> insertAudit(
                UUID.randomUUID(),
                (short) 1,
                "USER",
                null,
                "LOAN_APPLICATION",
                loanApplicationId,
                "APPLICATION_SUBMITTED",
                "{}"
        ));
        assertDatabaseRejects(() -> insertAudit(
                UUID.randomUUID(),
                (short) 1,
                "SYSTEM",
                USER_ID,
                "LOAN_APPLICATION",
                loanApplicationId,
                "OFFER_EXPIRED",
                "{}"
        ));
        assertDatabaseRejects(() -> insertAudit(
                UUID.randomUUID(),
                (short) 1,
                "SYSTEM",
                null,
                "LOAN_APPLICATION",
                loanApplicationId,
                "OFFER_EXPIRED",
                "[]"
        ));

        UUID operationId = UUID.randomUUID();
        insertAudit(operationId, (short) 1, "USER", USER_ID, "LOAN_APPLICATION", loanApplicationId, "APPLICATION_SUBMITTED", "{}");
        assertDatabaseRejects(() -> insertAudit(operationId, (short) 1, "USER", USER_ID, "LOAN_APPLICATION", loanApplicationId, "REVIEW_STARTED", "{}"));

        UUID auditEventId = UUID.randomUUID();
        insertAuditWithId(auditEventId, UUID.randomUUID(), loanApplicationId);
        assertDatabaseRejects(() -> jdbcTemplate.update(
                "UPDATE audit_events SET action = action WHERE id = ?",
                auditEventId
        ));
        assertDatabaseRejects(() -> jdbcTemplate.update(
                "DELETE FROM audit_events WHERE id = ?",
                auditEventId
        ));
    }

    @Test
    void recordsAuditEventThroughSynchronousListenerAndJpaAdapter() {
        UUID loanApplicationId = insertLoanApplication();
        UUID operationId = UUID.randomUUID();

        applicationEventPublisher.publishEvent(new AuditRecordRequestedEvent(
                operationId,
                (short) 7,
                ActionActor.user(USER_ID),
                AuditEntityType.LOAN_APPLICATION,
                loanApplicationId,
                AuditAction.APPLICATION_SUBMITTED,
                List.of(new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, "SALARY_ADVANCE")),
                NOW
        ));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT
                            operation_id,
                            sequence_number,
                            actor_type,
                            actor_user_id,
                            jsonb_typeof(payload) AS payload_type,
                            payload ->> 'PRODUCT_CODE' AS product_code,
                            created_at
                        FROM audit_events
                        WHERE entity_type = 'LOAN_APPLICATION'
                          AND entity_id = ?
                          AND action = 'APPLICATION_SUBMITTED'
                        """,
                loanApplicationId
        );
        assertEquals(operationId, row.get("operation_id"));
        assertEquals(7, ((Number) row.get("sequence_number")).intValue());
        assertEquals("USER", row.get("actor_type"));
        assertEquals(USER_ID, row.get("actor_user_id"));
        assertEquals("object", row.get("payload_type"));
        assertEquals("SALARY_ADVANCE", row.get("product_code"));
        assertNotNull(row.get("created_at"));
    }

    @Test
    void recordsLoanApplicationStatusTransitionThroughJpaAdapter() {
        UUID loanApplicationId = insertLoanApplication();
        UUID operationId = UUID.randomUUID();

        transitionRepository.saveAll(List.of(new LoanApplicationStatusTransition(
                UUID.randomUUID(),
                loanApplicationId,
                operationId,
                (short) 1,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationTransitionAction.REVIEW_STARTED,
                "ready for review",
                ActionActor.user(USER_ID),
                NOW
        )));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT
                            operation_id,
                            sequence_number,
                            from_status,
                            to_status,
                            action,
                            reason,
                            actor_type,
                            actor_user_id,
                            created_at
                        FROM loan_application_status_transitions
                        WHERE loan_application_id = ?
                          AND action = 'REVIEW_STARTED'
                        """,
                loanApplicationId
        );
        assertEquals(operationId, row.get("operation_id"));
        assertEquals(1, ((Number) row.get("sequence_number")).intValue());
        assertEquals("SUBMITTED", row.get("from_status"));
        assertEquals("UNDER_REVIEW", row.get("to_status"));
        assertEquals("ready for review", row.get("reason"));
        assertEquals("USER", row.get("actor_type"));
        assertEquals(USER_ID, row.get("actor_user_id"));
        assertNotNull(row.get("created_at"));
    }
    private UUID insertLoanApplication() {
        UUID loanApplicationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String applicationNumber = "SA-IT-" + loanApplicationId.toString().substring(0, 8);

        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO loan_applications (
                            id,
                            customer_id,
                            loan_product_id,
                            application_number,
                            product_code,
                            product_type,
                            status,
                            requested_amount,
                            requested_term_months,
                            submitted_at
                        )
                        SELECT ?, ?, id, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'SUBMITTED', 1000000.00, 1, ?
                        FROM loan_products
                        WHERE product_code = 'SALARY_ADVANCE'
                        """,
                loanApplicationId,
                customerId,
                applicationNumber,
                Timestamp.valueOf(NOW)
        );
        assertEquals(1, inserted, "SALARY_ADVANCE seed product must exist");
        return loanApplicationId;
    }

    private int insertHistory(
            UUID operationId,
            short sequenceNumber,
            UUID loanApplicationId,
            String fromStatus,
            String toStatus,
            String action,
            String actorType,
            UUID actorUserId
    ) {
        return jdbcTemplate.update(
                """
                        INSERT INTO loan_application_status_transitions (
                            loan_application_id,
                            operation_id,
                            sequence_number,
                            from_status,
                            to_status,
                            action,
                            actor_type,
                            actor_user_id,
                            occurred_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                loanApplicationId,
                operationId,
                sequenceNumber,
                fromStatus,
                toStatus,
                action,
                actorType,
                actorUserId,
                Timestamp.valueOf(NOW)
        );
    }

    private void insertHistoryWithId(UUID transitionId, UUID operationId, UUID loanApplicationId) {
        jdbcTemplate.update(
                """
                        INSERT INTO loan_application_status_transitions (
                            id,
                            loan_application_id,
                            operation_id,
                            sequence_number,
                            from_status,
                            to_status,
                            action,
                            actor_type,
                            actor_user_id,
                            occurred_at
                        )
                        VALUES (?, ?, ?, 1, 'SUBMITTED', 'UNDER_REVIEW', 'REVIEW_STARTED', 'USER', ?, ?)
                        """,
                transitionId,
                loanApplicationId,
                operationId,
                USER_ID,
                Timestamp.valueOf(NOW)
        );
    }

    private int insertAudit(
            UUID operationId,
            short sequenceNumber,
            String actorType,
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String action,
            String payload
    ) {
        return jdbcTemplate.update(
                """
                        INSERT INTO audit_events (
                            operation_id,
                            sequence_number,
                            actor_type,
                            actor_user_id,
                            entity_type,
                            entity_id,
                            action,
                            payload,
                            occurred_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                operationId,
                sequenceNumber,
                actorType,
                actorUserId,
                entityType,
                entityId,
                action,
                payload,
                Timestamp.valueOf(NOW)
        );
    }

    private void insertAuditWithId(UUID auditEventId, UUID operationId, UUID loanApplicationId) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_events (
                            id,
                            operation_id,
                            sequence_number,
                            actor_type,
                            actor_user_id,
                            entity_type,
                            entity_id,
                            action,
                            payload,
                            occurred_at
                        )
                        VALUES (?, ?, 1, 'USER', ?, 'LOAN_APPLICATION', ?, 'APPLICATION_SUBMITTED', '{}'::jsonb, ?)
                        """,
                auditEventId,
                operationId,
                USER_ID,
                loanApplicationId,
                Timestamp.valueOf(NOW)
        );
    }

    private void assertDatabaseRejects(DatabaseOperation operation) {
        assertThrows(DataAccessException.class, operation::execute);
    }

    @FunctionalInterface
    private interface DatabaseOperation {
        void execute();
    }
}

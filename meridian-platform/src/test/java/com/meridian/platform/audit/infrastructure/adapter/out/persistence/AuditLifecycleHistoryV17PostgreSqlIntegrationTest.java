package com.meridian.platform.audit.infrastructure.adapter.out.persistence;

import com.meridian.platform.audit.infrastructure.adapter.in.event.BusinessAuditEventListener;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionFact;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
class AuditLifecycleHistoryV17PostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BusinessAuditEventListener businessAuditEventListener;

    @Autowired
    private LoanApplicationStatusTransitionRecorder transitionRecorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @Test
    void loanTransitionsAcceptValidUserAndSystemRows() {
        UUID applicationId = insertLoanApplication("SUBMITTED");
        insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                "USER",
                CUSTOMER_USER_ID
        );
        insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                2,
                "SUBMITTED",
                "UNDER_REVIEW",
                "START_REVIEW",
                "USER",
                LOAN_OFFICER_USER_ID
        );

        UUID expiryApplicationId = insertLoanApplication("CUSTOMER_ACCEPTANCE_PENDING");
        insertLoanTransition(
                expiryApplicationId,
                UUID.randomUUID(),
                1,
                "CUSTOMER_ACCEPTANCE_PENDING",
                "EXPIRED",
                "EXPIRE_APPROVED_OFFER",
                "SYSTEM",
                null
        );

        assertEquals(2, countLoanTransitions(applicationId));
        assertEquals(1, countLoanTransitions(expiryApplicationId));
    }

    @Test
    void loanTransitionsRejectInvalidActorCombinations() {
        UUID applicationId = insertLoanApplication("SUBMITTED");

        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                "USER",
                null
        ));
        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                "SYSTEM",
                CUSTOMER_USER_ID
        ));
    }

    @Test
    void loanTransitionsRejectInvalidInitialRulesStatusesActionsAndDuplicateSequences() {
        UUID applicationId = insertLoanApplication("SUBMITTED");
        insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                "USER",
                CUSTOMER_USER_ID
        );

        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                insertLoanApplication("SUBMITTED"),
                UUID.randomUUID(),
                1,
                null,
                "EXPIRED",
                "EXPIRE_APPROVED_OFFER",
                "SYSTEM",
                null
        ));
        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                insertLoanApplication("SUBMITTED"),
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "START_REVIEW",
                "USER",
                CUSTOMER_USER_ID
        ));
        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                "SUBMITTED",
                "UNDER_REVIEW",
                "START_REVIEW",
                "USER",
                LOAN_OFFICER_USER_ID
        ));
        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                insertLoanApplication("SUBMITTED"),
                UUID.randomUUID(),
                1,
                "SUBMITTED",
                "NOT_A_STATUS",
                "START_REVIEW",
                "USER",
                LOAN_OFFICER_USER_ID
        ));
        assertThrows(DataAccessException.class, () -> insertLoanTransition(
                insertLoanApplication("SUBMITTED"),
                UUID.randomUUID(),
                1,
                "SUBMITTED",
                "UNDER_REVIEW",
                "BAD_ACTION",
                "USER",
                LOAN_OFFICER_USER_ID
        ));
    }

    @Test
    void loanTransitionsRejectUpdateDeleteAndPopulateCreatedAtThroughRealAdapter() {
        UUID applicationId = insertLoanApplication("SUBMITTED");
        UUID rowId = insertLoanTransition(
                applicationId,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                "USER",
                CUSTOMER_USER_ID
        );

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update " + table("loan_application_status_transitions") + " set reason = 'changed' where id = ?",
                rowId
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from " + table("loan_application_status_transitions") + " where id = ?",
                rowId
        ));

        UUID adapterApplicationId = insertLoanApplication("SUBMITTED");
        BusinessOperationContext context = BusinessOperationContext.user(UUID.randomUUID(), CUSTOMER_USER_ID, NOW);
        transactionTemplate.executeWithoutResult(status -> transitionRecorder.record(
                context,
                List.of(new LoanApplicationTransitionFact(
                        adapterApplicationId,
                        null,
                        LoanApplicationStatus.SUBMITTED,
                        LoanApplicationTransitionAction.SUBMIT_APPLICATION
                )),
                null
        ));

        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "select created_at from " + table("loan_application_status_transitions") + " where loan_application_id = ?",
                LocalDateTime.class,
                adapterApplicationId
        );
        assertNotNull(createdAt);
    }

    @Test
    void auditEventsPersistValidUserPayloadThroughProductionListenerAsJsonObject() {
        UUID operationId = UUID.randomUUID();
        UUID applicationId = insertLoanApplication("SUBMITTED");

        transactionTemplate.executeWithoutResult(status -> businessAuditEventListener.onBusinessAuditEvent(
                BusinessAuditEvent.single(
                        BusinessOperationContext.user(operationId, CUSTOMER_USER_ID, NOW),
                        new BusinessAuditEntry(
                                BusinessAuditAction.SALARY_ADVANCE_APPLICATION_SUBMITTED,
                                BusinessAuditEntityType.LOAN_APPLICATION,
                                applicationId,
                                BusinessAuditPayload.builder()
                                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, applicationId)
                                        .build()
                        )
                )
        ));

        assertEquals("object", jdbcTemplate.queryForObject(
                "select jsonb_typeof(payload) from " + table("audit_events") + " where operation_id = ?",
                String.class,
                operationId
        ));
        assertEquals(applicationId.toString(), jdbcTemplate.queryForObject(
                "select payload ->> 'loanApplicationId' from " + table("audit_events") + " where operation_id = ?",
                String.class,
                operationId
        ));
        assertNotNull(jdbcTemplate.queryForObject(
                "select created_at from " + table("audit_events") + " where operation_id = ?",
                LocalDateTime.class,
                operationId
        ));
    }

    @Test
    void auditEventsAcceptValidSystemEventThroughProductionListener() {
        UUID operationId = UUID.randomUUID();
        UUID applicationId = insertLoanApplication("EXPIRED");

        transactionTemplate.executeWithoutResult(status -> businessAuditEventListener.onBusinessAuditEvent(
                BusinessAuditEvent.single(
                        BusinessOperationContext.system(operationId, NOW),
                        BusinessAuditEntry.of(
                                BusinessAuditAction.OFFER_EXPIRED,
                                BusinessAuditEntityType.APPROVED_OFFER,
                                applicationId
                        )
                )
        ));

        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select actor_user_id is null from " + table("audit_events") + " where operation_id = ?",
                Boolean.class,
                operationId
        )));
    }

    @Test
    void auditEventsRejectInvalidActorCombinationsJsonEntityActionAndDuplicateSequences() {
        UUID entityId = insertLoanApplication("SUBMITTED");

        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                null,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        ));
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "SYSTEM",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        ));
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "[]"
        ));
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                oversizedJson()
        ));
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                CUSTOMER_USER_ID,
                "BAD_ENTITY",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        ));
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "BAD_ACTION",
                "{}"
        ));

        UUID operationId = UUID.randomUUID();
        insertAuditEvent(
                operationId,
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        );
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                operationId,
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        ));
    }

    @Test
    void auditEventsRejectUpdateAndDelete() {
        UUID entityId = insertLoanApplication("SUBMITTED");
        UUID auditEventId = insertAuditEvent(
                UUID.randomUUID(),
                1,
                "USER",
                CUSTOMER_USER_ID,
                "LOAN_APPLICATION",
                entityId,
                "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                "{}"
        );

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update " + table("audit_events") + " set action = action where id = ?",
                auditEventId
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from " + table("audit_events") + " where id = ?",
                auditEventId
        ));
    }

    private UUID insertLoanApplication(String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.loan_applications (
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
                        ) values (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', ?, ?, 1, ?)
                        """.formatted(TEST_SCHEMA),
                id,
                UUID.randomUUID(),
                salaryAdvanceProductId(),
                "SA-TEST-" + id,
                status,
                BigDecimal.valueOf(3_000_000).setScale(2),
                NOW
        );
        return id;
    }

    private UUID insertLoanTransition(
            UUID loanApplicationId,
            UUID operationId,
            int sequenceNumber,
            String fromStatus,
            String toStatus,
            String action,
            String actorType,
            UUID actorUserId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.loan_application_status_transitions (
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
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(TEST_SCHEMA),
                id,
                loanApplicationId,
                operationId,
                sequenceNumber,
                fromStatus,
                toStatus,
                action,
                actorType,
                actorUserId,
                NOW
        );
        return id;
    }

    private UUID insertAuditEvent(
            UUID operationId,
            int sequenceNumber,
            String actorType,
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String action,
            String payloadJson
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.audit_events (
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
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """.formatted(TEST_SCHEMA),
                id,
                operationId,
                sequenceNumber,
                actorType,
                actorUserId,
                entityType,
                entityId,
                action,
                payloadJson,
                NOW
        );
        return id;
    }

    private int countLoanTransitions(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table("loan_application_status_transitions") + " where loan_application_id = ?",
                Integer.class,
                loanApplicationId
        );
    }

    private UUID salaryAdvanceProductId() {
        return jdbcTemplate.queryForObject(
                "select id from " + table("loan_products") + " where product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
    }

    private String table(String tableName) {
        return TEST_SCHEMA + "." + tableName;
    }

    private String oversizedJson() {
        return "{\"loanApplicationId\":\"" + "A".repeat(2050) + "\"}";
    }
}

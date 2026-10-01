package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.port.in.QueryStaffReviewHistoryUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"})
class StaffReviewHistoryPostgreSqlIntegrationTest {
    private static final String SCHEMA = "review_history_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID OFFICER = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 1, 8, 0);
    @Autowired JdbcTemplate jdbc;
    @Autowired QueryStaffReviewHistoryUseCase history;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @AfterEach
    void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void composesStoredCyclesExactLinksAndCurrentActorNoteEntitlementsWithoutWrites() {
        UUID customer = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID firstRecommendation = UUID.randomUUID();
        UUID secondRecommendation = UUID.randomUUID();
        UUID decision = UUID.randomUUID();
        jdbc.update("insert into customers (id,customer_number,status,verification_status,profile_completion_status) "
                + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')", customer, "HISTORY-" + customer);
        jdbc.update("insert into loan_applications (id,customer_id,loan_product_id,application_number,product_code,"
                + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                + "values (?,?,(select id from loan_products where product_code='UNSECURED_CONSUMER_LOAN'),?,"
                + "'UNSECURED_CONSUMER_LOAN','UNSECURED','UNDER_REVIEW',5000000,6,?)",
                application, customer, "UCL-HISTORY-" + application, TIME);
        insertCycle(application, third, 3, "ACTIVE", null);
        insertCycle(application, first, 1, "COMPLETED", TIME.plusMinutes(30));
        insertCycle(application, second, 2, "SUPERSEDED", TIME.plusHours(1));
        insertRecommendation(application, first, firstRecommendation, TIME.plusMinutes(10));
        insertRecommendation(application, second, secondRecommendation, TIME.plusMinutes(10));
        jdbc.update("insert into approval_decisions (id,loan_application_id,review_recommendation_id,approver_user_id,"
                + "decision,reason,internal_notes,decided_at) values (?,?,?,?,'RETURN_TO_LOAN_OFFICER_REVIEW',?,?,?)",
                decision, application, firstRecommendation, APPROVER, "Recheck the evidence", "Synthetic decision context", TIME.plusMinutes(30));
        authenticate(OFFICER, "loan:review");
        var officerRead = history.query(application);
        assertEquals(List.of(1, 2, 3), officerRead.cycles().stream().map(c -> c.cycleNumber()).toList());
        var completed = officerRead.cycles().getFirst();
        assertEquals(first, completed.reviewCycleId());
        assertEquals(firstRecommendation, completed.recommendation().recommendationId());
        assertEquals(first, completed.recommendation().reviewCycleId());
        assertEquals(firstRecommendation, completed.decision().reviewRecommendationId());
        assertEquals(decision, completed.decision().decisionId());
        assertEquals("Evidence reviewed", completed.recommendation().reason());
        assertEquals("Recheck the evidence", completed.decision().reason());
        assertEquals("Synthetic recommendation context", completed.recommendation().internalNotes());
        assertEquals("Synthetic decision context", completed.decision().internalNotes());
        assertEquals(OFFICER, completed.assignedLoanOfficer().userId());
        assertEquals(APPROVER, completed.decision().recordedBy().userId());
        assertNull(officerRead.cycles().get(1).decision());
        assertNull(officerRead.cycles().getLast().recommendation());
        authenticate(APPROVER, "approval:decide");
        assertEquals("Synthetic recommendation context", history.query(application).cycles().getFirst().recommendation().internalNotes());
        authenticate(UUID.randomUUID(), "loan:review");
        var peer = history.query(application).cycles().getFirst();
        assertFalse(peer.recommendation().internalNoteReadable());
        assertFalse(peer.decision().internalNoteReadable());
        assertNull(peer.recommendation().internalNotes());
        assertNull(peer.decision().internalNotes());
        assertNotNull(peer.recommendation().reason());
        assertTrue(officerRead.cycles().getFirst().decision().internalNoteReadable());
        assertEquals(3, jdbc.queryForObject("select count(*) from loan_application_review_cycles where loan_application_id=?", Integer.class, application));
        assertEquals("Synthetic decision context", jdbc.queryForObject("select internal_notes from approval_decisions where id=?", String.class, decision));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
    }

    private void insertCycle(UUID application, UUID cycle, int number, String status, LocalDateTime ended) {
        jdbc.update("insert into loan_application_review_cycles (id,loan_application_id,cycle_number,"
                + "assigned_loan_officer_user_id,status,started_at,ended_at) values (?,?,?,?,?,?,?)",
                cycle, application, number, OFFICER, status, TIME, ended);
    }

    private void insertRecommendation(UUID application, UUID cycle, UUID recommendation, LocalDateTime time) {
        jdbc.update("insert into review_recommendations (id,loan_application_id,review_cycle_id,loan_officer_user_id,"
                + "recommendation,reason,internal_notes,submitted_at) values (?,?,?,?,'RECOMMEND_APPROVAL',?,?,?)",
                recommendation, application, cycle, OFFICER, "Evidence reviewed", "Synthetic recommendation context", time);
    }

    private static void authenticate(UUID id, String permission) {
        var actor = new AuthenticatedUser(id, "history@meridian.local", "STAFF", null, Set.of(), Set.of(permission));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    }
}

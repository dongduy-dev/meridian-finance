package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.dto.*;
import com.meridian.platform.partner.application.port.in.*;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewDecision;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PartnerSnapshotAuthorityPostgreSqlIntegrationTest {
    private static final String SCHEMA = "partner_snapshot_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired ImportPartnerEmployeesUseCase imports;
    @Autowired VerifyPartnerEmployeeUseCase verification;
    @Autowired QueryPartnerEmployeeUseCase employees;
    @Autowired QueryPartnerEligibilityReviewUseCase reviewQueries;
    @Autowired DecidePartnerEligibilityReviewUseCase decisions;
    @Autowired QueryCustomerPartnerEmployeeLinkUseCase eligibility;
    @Autowired QueryOwnPartnerEmployeeVerificationUseCase ownReviews;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired javax.sql.DataSource dataSource;
    @MockitoBean CurrentUserProvider users;
    @MockitoBean CustomerIdentityEvidencePort identity;
    @MockitoBean BusinessAuditPublisher audit;

    private final ThreadLocal<AuthenticatedUser> actors = new ThreadLocal<>();
    private UUID customerId;
    private UUID companyA;
    private UUID companyB;
    private UUID companyC;
    private String month;
    private String identityReference;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void seed() {
        customerId = UUID.randomUUID();
        month = YearMonth.now(clock).toString();
        identityReference = "FICTIONAL-" + customerId;
        jdbc.update("INSERT INTO customers (id, customer_number, status, verification_status, profile_completion_status) "
                + "VALUES (?, ?, 'ACTIVE', 'VERIFIED', 'COMPLETE')", customerId, "CUS-" + customerId);
        companyA = company(); companyB = company(); companyC = company();
        actors.set(staff());
        when(users.currentUser()).thenAnswer(invocation -> actors.get());
        when(identity.findIdentityEvidenceByCustomerId(customerId)).thenReturn(Optional.of(
                new CustomerIdentityEvidenceSnapshot(customerId, true, true, true, identityReference)));
    }

    @Test
    void invalidReplacementPreservesAuthorityLinkReviewAndReplayWithoutAnyEmployeeWrites() {
        var first = snapshot(companyA, "EMP-1", true);
        asCustomer(); verify(companyA, "EMP-1");
        verify(companyB, "MISSING");
        String linksBefore = linkEvidence();
        String reviewsBefore = reviewEvidence();
        actors.set(staff());
        var request = new ImportPartnerEmployeesRequest(UUID.randomUUID(), month, List.of(
                row("EMP-1", true),
                new PartnerEmployeeImportRowRequest("BAD", identityReference, new BigDecimal("-1"),
                        BigDecimal.ONE, "ACTIVE", true)));
        var failed = imports.importEmployees(companyA, request);
        assertEquals("FAILED", failed.status());
        assertEquals(1, failed.validRowCount());
        assertEquals(1, failed.invalidRowCount());
        assertEquals("INVALID_SALARY_AMOUNT", failed.rejections().getFirst().errorCode());
        assertFalse(failed.rejections().toString().contains(identityReference));
        assertEquals(first.importBatchId(), employees.getCurrentPartnerEmployeeSnapshot(companyA).authoritativeBatchId());
        assertEquals(0, count("SELECT COUNT(*) FROM partner_employees WHERE import_batch_id=?", failed.importBatchId()));
        assertEquals(linksBefore, linkEvidence());
        assertEquals(reviewsBefore, reviewEvidence());
        assertEquals(failed, imports.importEmployees(companyA, request));
        assertEquals(1, count("SELECT COUNT(*) FROM partner_employee_import_batches WHERE request_id=?", request.requestId()));
        assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(BusinessStateConflictException.class,
                () -> imports.importEmployees(companyA, new ImportPartnerEmployeesRequest(request.requestId(), month,
                        List.of(row("EMP-1", true))))).getErrorCode());
    }

    @Test
    void replacementIsCompleteRosterAndPreservesHistoricalEmployeeRows() {
        var first = imports.importEmployees(companyA, new ImportPartnerEmployeesRequest(UUID.randomUUID(), month,
                List.of(row("EMP-OLD", true), row("EMP-RETAIN", true))));
        var second = snapshot(companyA, "EMP-RETAIN", true);
        var current = employees.getCurrentPartnerEmployeeSnapshot(companyA);
        assertEquals(second.importBatchId(), current.authoritativeBatchId());
        assertEquals(List.of("EMP-RETAIN"), current.employees().stream().map(PartnerEmployeeDto::employeeCode).toList());
        assertEquals(2, count("SELECT COUNT(*) FROM partner_employees WHERE import_batch_id=?", first.importBatchId()));
        assertEquals(3, employees.getPartnerEmployeesByCompanyId(companyA, false).size());
        assertTrue(current.employees().getFirst().maskedIdentityReference().startsWith("****"));
        assertFalse(current.toString().contains(identityReference));
    }

    @Test
    void deterministicAuthorityUsesBatchIdWhenCreationTimesTie() {
        var first = snapshot(companyA, "EMP-FIRST", true);
        var second = snapshot(companyA, "EMP-SECOND", true);
        jdbc.update("UPDATE partner_employee_import_batches SET created_at=TIMESTAMP '2026-01-01 00:00:00' WHERE id IN (?, ?)",
                first.importBatchId(), second.importBatchId());
        UUID expected = first.importBatchId().toString().compareTo(second.importBatchId().toString()) > 0
                ? first.importBatchId() : second.importBatchId();
        assertEquals(expected, employees.getCurrentPartnerEmployeeSnapshot(companyA).authoritativeBatchId());
        assertEquals(1, employees.getCurrentPartnerEmployeeSnapshot(companyA).employees().size());
    }

    @Test
    void importRefreshKeepsCustomerDeclaredAlternativeUnderReview() {
        snapshot(companyA, "EMP-A", true);
        asCustomer(); verify(companyA, "EMP-A"); verify(companyB, "MISSING-B");
        UUID reviewB = pending(companyB);
        actors.set(staff());
        var refreshed = snapshot(companyA, "EMP-A", true);
        assertEquals(refreshed.importBatchId(), jdbc.queryForObject(
                "SELECT source_import_batch_id FROM customer_partner_employee_links WHERE customer_id=? AND link_status='VERIFIED'",
                UUID.class, customerId));
        assertEquals("PENDING", status(reviewB));
        assertEquals("NOT_VERIFIED", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    @Test
    void replacementWithChangedIdentityCannotRewriteHistoricalVerifiedProof() {
        snapshot(companyA, "EMP-A", true);
        asCustomer(); verify(companyA, "EMP-A");
        String before = linkEvidence();
        actors.set(staff());
        imports.importEmployees(companyA, new ImportPartnerEmployeesRequest(UUID.randomUUID(), month, List.of(
                new PartnerEmployeeImportRowRequest("EMP-A", "FICTIONAL-DIFFERENT-IDENTITY", new BigDecimal("12000000"),
                        new BigDecimal("4000000"), "ACTIVE", true))));
        assertEquals(before, linkEvidence());
        assertEquals("EVIDENCE_STALE", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    @Test
    void v72UpgradePreservesAuditVocabularyAndPermitsFailedSnapshotEvidence() throws Exception {
        String schema = "snapshot_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            var previous = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).target("71").load();
            previous.migrate();
            var next = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).target("72").load();
            assertEquals(1, next.migrate().migrationsExecuted);
            String constraint = jdbc.queryForObject("SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                    + "JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname=? AND c.conname='chk_audit_events_action'",
                    String.class, schema);
            for (var action : com.meridian.platform.shared.domain.audit.BusinessAuditAction.values()) {
                assertTrue(constraint.contains("'" + action.name() + "'"), action.name());
            }
            String snapshot = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql"));
            assertTrue(snapshot.contains("Snapshot source: migrations V1 through V72"));
            assertTrue(snapshot.contains("PARTNER_EMPLOYEE_IMPORT_FAILED"));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void replacedReviewCannotBeDecidedOrRetargetedAndFreshExactVerificationRecovers() {
        var first = snapshot(companyA, "EMP-OLD", true);
        asCustomer(); verify(companyA, "EMP-NEW");
        UUID oldReview = pending(companyA);
        actors.set(staff());
        var second = snapshot(companyA, "EMP-NEW", true);
        var historical = reviewQueries.queryReview(oldReview);
        assertEquals("SOURCE_BATCH_REPLACED", historical.nonReviewableReason());
        assertFalse(historical.approvalAvailable()); assertFalse(historical.rejectionAvailable());
        assertEquals("PARTNER_ELIGIBILITY_REVIEW_STALE", assertThrows(BusinessStateConflictException.class,
                () -> decisions.decide(oldReview, approval(employee(second.importBatchId())))).getErrorCode());
        asCustomer();
        assertEquals("MATCHED_ACTIVE", verify(companyA, "EMP-NEW").outcome());
        assertEquals("ELIGIBLE", eligibility.inspectCurrentEligibility(customerId).status().name());
        assertEquals(List.of(), ownReviews.getCurrentOwnVerifications());
        assertEquals("SUPERSEDED", status(oldReview));
        assertEquals(first.importBatchId(), jdbc.queryForObject(
                "SELECT source_import_batch_id FROM partner_eligibility_reviews WHERE id=?", UUID.class, oldReview));
    }

    @Test
    void unresolvedReverificationCreatesCurrentReviewOnceAndPreservesReplacedSource() {
        var first = snapshot(companyA, "EMP-1", true);
        asCustomer(); verify(companyA, "MISSING");
        UUID oldReview = pending(companyA);
        actors.set(staff()); var second = snapshot(companyA, "EMP-2", true);
        asCustomer(); verify(companyA, "MISSING");
        UUID currentReview = pending(companyA);
        assertNotEquals(oldReview, currentReview);
        assertEquals("SUPERSEDED", status(oldReview));
        assertEquals(first.importBatchId(), jdbc.queryForObject(
                "SELECT source_import_batch_id FROM partner_eligibility_reviews WHERE id=?", UUID.class, oldReview));
        assertEquals(second.importBatchId(), jdbc.queryForObject(
                "SELECT source_import_batch_id FROM partner_eligibility_reviews WHERE id=?", UUID.class, currentReview));
        verify(companyA, "MISSING"); assertEquals(currentReview, pending(companyA));
        assertEquals("NOT_VERIFIED", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    @Test
    void exactCurrentEmploymentSupersedesAlternativeReviewsButLeavesPriorMonthHistory() {
        snapshot(companyC, "EMP-C", true);
        asCustomer(); verify(companyA, "MISSING-A"); verify(companyB, "MISSING-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        UUID historicalCompany = company();
        UUID historicalReview = UUID.randomUUID();
        jdbc.update("INSERT INTO partner_eligibility_reviews (id, customer_id, partner_company_id, effective_month, "
                + "trigger_outcome, requested_employee_code, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'NOT_FOUND', 'PAST', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                historicalReview, customerId, historicalCompany, YearMonth.parse(month).minusMonths(1).toString());
        assertEquals("MATCHED_ACTIVE", verify(companyC, "EMP-C").outcome());
        assertEquals("SUPERSEDED", status(reviewA)); assertEquals("SUPERSEDED", status(reviewB));
        assertEquals("PENDING", status(historicalReview));
        assertEquals("ELIGIBLE", eligibility.inspectCurrentEligibility(customerId).status().name());
        assertTrue(ownReviews.getCurrentOwnVerifications().isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=? AND link_status='VERIFIED'", customerId));
    }

    @Test
    void manualApprovalSupersedesAlternativesAndReplayRemainsExact() {
        var source = snapshot(companyB, "EMP-B", true);
        asCustomer(); verify(companyA, "MISSING-A"); verify(companyB, "MISSING-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        actors.set(staff());
        var command = approval(employee(source.importBatchId()));
        var approved = decisions.decide(reviewB, command);
        assertEquals("APPROVED", approved.status()); assertEquals("SUPERSEDED", status(reviewA));
        assertEquals(approved, decisions.decide(reviewB, command));
        assertEquals("ELIGIBLE", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    @Test
    void unresolvedInactiveAndRejectedAttemptsNeverSupersedeAlternativeReviews() {
        var source = snapshot(companyC, "INACTIVE", false);
        asCustomer(); verify(companyA, "MISSING-A"); verify(companyB, "MISSING-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        assertTrue(verify(companyC, "MISSING-C").manualReviewRequired());
        UUID reviewC = pending(companyC);
        actors.set(staff());
        decisions.decide(reviewC, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.REJECT, null, PartnerEligibilityReviewReason.NO_ELIGIBLE_CURRENT_EMPLOYEE));
        assertEquals("PENDING", status(reviewA)); assertEquals("PENDING", status(reviewB));
        asCustomer(); assertEquals("MATCHED_INACTIVE", verify(companyC, "INACTIVE").outcome());
        assertEquals("PENDING", status(reviewA)); assertEquals("PENDING", status(reviewB));
        assertEquals("NOT_VERIFIED", eligibility.inspectCurrentEligibility(customerId).status().name());
        assertEquals(0, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=?", customerId));
        assertEquals(source.importBatchId(), employees.getCurrentPartnerEmployeeSnapshot(companyC).authoritativeBatchId());
    }

    @Test
    void auditFailureRollsBackManualEstablishmentAndCompetingSupersession() {
        var source = snapshot(companyB, "EMP-B", true);
        asCustomer(); verify(companyA, "MISSING-A"); verify(companyB, "MISSING-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        actors.set(staff()); doThrow(new IllegalStateException("Simulated audit failure")).when(audit).publish(any());
        assertThrows(IllegalStateException.class, () -> decisions.decide(reviewB, approval(employee(source.importBatchId()))));
        assertEquals("PENDING", status(reviewA)); assertEquals("PENDING", status(reviewB));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=?", customerId));
    }

    @Test
    void competingExactVerificationCommandsKeepOneCurrentLinkAndSupersedeBothAlternatives() throws Exception {
        snapshot(companyA, "EMP-A", true); snapshot(companyB, "EMP-B", true);
        asCustomer(); verify(companyA, "WRONG-A"); verify(companyB, "WRONG-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        var start = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { asCustomer(); start.await(5, TimeUnit.SECONDS); return verify(companyA, "EMP-A"); });
            var second = executor.submit(() -> { asCustomer(); start.await(5, TimeUnit.SECONDS); return verify(companyB, "EMP-B"); });
            start.await(5, TimeUnit.SECONDS);
            assertEquals("MATCHED_ACTIVE", first.get(15, TimeUnit.SECONDS).outcome());
            assertEquals("MATCHED_ACTIVE", second.get(15, TimeUnit.SECONDS).outcome());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=? AND link_status='VERIFIED'", customerId));
        assertEquals(1, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=? AND link_status='DISABLED'", customerId));
        assertEquals("SUPERSEDED", status(reviewA)); assertEquals("SUPERSEDED", status(reviewB));
        assertEquals("ELIGIBLE", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    @Test
    void manualApprovalRacingExactVerificationCannotLeaveContradictoryCurrentEmployment() throws Exception {
        snapshot(companyA, "EMP-A", true); var sourceB = snapshot(companyB, "EMP-B", true);
        asCustomer(); verify(companyA, "WRONG-A"); verify(companyB, "WRONG-B");
        UUID reviewA = pending(companyA); UUID reviewB = pending(companyB);
        var approval = approval(employee(sourceB.importBatchId()));
        var start = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var exact = executor.submit(() -> { asCustomer(); start.await(5, TimeUnit.SECONDS); return verify(companyA, "EMP-A"); });
            var manual = executor.submit(() -> {
                actors.set(staff()); start.await(5, TimeUnit.SECONDS);
                try { return decisions.decide(reviewB, approval).status(); }
                catch (BusinessStateConflictException e) { return e.getErrorCode(); }
            });
            start.await(5, TimeUnit.SECONDS);
            assertEquals("MATCHED_ACTIVE", exact.get(15, TimeUnit.SECONDS).outcome());
            assertTrue(List.of("APPROVED", "PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED").contains(manual.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id=? AND link_status='VERIFIED'", customerId));
        assertEquals("SUPERSEDED", status(reviewA));
        assertTrue(List.of("APPROVED", "SUPERSEDED").contains(status(reviewB)));
        assertEquals("ELIGIBLE", eligibility.inspectCurrentEligibility(customerId).status().name());
    }

    private UUID company() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO partner_companies (id, company_code, name, status, salary_advance_policy_limit) "
                + "VALUES (?, ?, 'Fictional Snapshot Partner', 'ACTIVE', 20000000)", id, "PARTNER-" + id);
        return id;
    }
    private PartnerEmployeeImportRowRequest row(String code, boolean active) {
        return new PartnerEmployeeImportRowRequest(code, identityReference, new BigDecimal("12000000"),
                new BigDecimal("4000000"), "ACTIVE", active);
    }
    private PartnerEmployeeImportResultDto snapshot(UUID company, String code, boolean active) {
        return imports.importEmployees(company, new ImportPartnerEmployeesRequest(UUID.randomUUID(), month, List.of(row(code, active))));
    }
    private PartnerEmployeeVerificationDto verify(UUID company, String code) {
        return verification.verifyPartnerEmployee(company, new PartnerEmployeeVerificationRequest(code));
    }
    private UUID pending(UUID company) {
        return jdbc.queryForObject("SELECT id FROM partner_eligibility_reviews WHERE customer_id=? AND partner_company_id=? AND status='PENDING'",
                UUID.class, customerId, company);
    }
    private String status(UUID review) {
        return jdbc.queryForObject("SELECT status FROM partner_eligibility_reviews WHERE id=?", String.class, review);
    }
    private UUID employee(UUID batch) {
        return jdbc.queryForObject("SELECT id FROM partner_employees WHERE import_batch_id=?", UUID.class, batch);
    }
    private PartnerEligibilityReviewDecisionRequest approval(UUID employee) {
        return new PartnerEligibilityReviewDecisionRequest(PartnerEligibilityReviewDecision.APPROVE, employee,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED);
    }
    private String linkEvidence() {
        return jdbc.queryForObject("SELECT json_agg(l ORDER BY id)::text FROM customer_partner_employee_links l WHERE customer_id=?", String.class, customerId);
    }
    private String reviewEvidence() {
        return jdbc.queryForObject("SELECT json_agg(r ORDER BY id)::text FROM partner_eligibility_reviews r WHERE customer_id=?", String.class, customerId);
    }
    private int count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }
    private void asCustomer() {
        actors.set(new AuthenticatedUser(UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("partner:verify")));
    }
    private AuthenticatedUser staff() {
        return new AuthenticatedUser(UUID.randomUUID(), "admin@meridian.test", "STAFF", null,
                Set.of(), Set.of("partner:read", "partner:manage"));
    }
}

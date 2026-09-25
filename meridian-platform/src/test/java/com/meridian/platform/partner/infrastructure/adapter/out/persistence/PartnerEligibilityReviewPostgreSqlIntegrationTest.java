package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.port.in.DecidePartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
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

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PartnerEligibilityReviewPostgreSqlIntegrationTest {

    private static final String SCHEMA = "partner_review_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID REVIEW_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000099");
    private static final UUID COMPANY_ID = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID SECOND_COMPANY_ID = UUID.fromString("30000000-0000-4000-8000-000000000013");
    private static final UUID THIRD_COMPANY_ID = UUID.fromString("30000000-0000-4000-8000-000000000023");
    private static final UUID BATCH_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID EMPLOYEE_ID = UUID.fromString("50000000-0000-4000-8000-000000000005");

    @Autowired DecidePartnerEligibilityReviewUseCase decisions;
    @Autowired PartnerEligibilityReviewRepository reviews;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean CustomerIdentityEvidencePort identityEvidence;
    @MockitoBean BusinessAuditPublisher auditPublisher;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "meridian156"));
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update(
                "DELETE FROM partner_eligibility_reviews "
                        + "WHERE customer_id IN (?, ?) OR partner_company_id IN (?, ?, ?)",
                CUSTOMER_ID, OTHER_CUSTOMER_ID, COMPANY_ID, SECOND_COMPANY_ID, THIRD_COMPANY_ID
        );
        jdbcTemplate.update(
                "DELETE FROM customer_partner_employee_links WHERE customer_id = ? AND partner_company_id = ?",
                CUSTOMER_ID,
                COMPANY_ID
        );
        jdbcTemplate.update("DELETE FROM partner_employees WHERE id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM partner_employee_import_batches WHERE id = ?", BATCH_ID);
        jdbcTemplate.update(
                "DELETE FROM partner_companies WHERE id IN (?, ?, ?)",
                COMPANY_ID, SECOND_COMPANY_ID, THIRD_COMPANY_ID
        );
        jdbcTemplate.update("DELETE FROM customers WHERE id IN (?, ?)", CUSTOMER_ID, OTHER_CUSTOMER_ID);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:manage")
        ));
        when(identityEvidence.findIdentityEvidenceByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerIdentityEvidenceSnapshot(CUSTOMER_ID, true, true, "IDENTITY-001")
        ));
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        jdbcTemplate.update("""
                INSERT INTO customers (
                    id, customer_number, status, verification_status, profile_completion_status
                ) VALUES (?, 'CUS-REVIEW-001', 'ACTIVE', 'VERIFIED', 'COMPLETE')
                """, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO partner_companies (
                    id, company_code, name, status, salary_advance_policy_limit
                ) VALUES (?, ?, 'Concurrency Partner', 'ACTIVE', 20000000)
                """, COMPANY_ID, "REVIEW-" + UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO partner_employee_import_batches (
                    id, partner_company_id, effective_month, status, valid_row_count, invalid_row_count
                ) VALUES (?, ?, ?, 'COMPLETED', 1, 0)
                """, BATCH_ID, COMPANY_ID, month);
        jdbcTemplate.update("""
                INSERT INTO partner_employees (
                    id, partner_company_id, import_batch_id, employee_code, identity_reference,
                    salary_amount, salary_advance_limit, employment_status, active
                ) VALUES (?, ?, ?, 'EMP-001', 'IDENTITY-001', 12000000, 4000000, 'ACTIVE', TRUE)
                """, EMPLOYEE_ID, COMPANY_ID, BATCH_ID);
        jdbcTemplate.update("""
                INSERT INTO partner_eligibility_reviews (
                    id, customer_id, partner_company_id, effective_month, source_import_batch_id,
                    trigger_outcome, requested_employee_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'NOT_FOUND', 'EMP-001', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, REVIEW_ID, CUSTOMER_ID, COMPANY_ID, month, BATCH_ID);
    }

    @Test
    void competingApprovalAndRejectionProduceOneTerminalOutcome() throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        var approve = new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.APPROVE,
                EMPLOYEE_ID,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
        );
        var reject = new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.REJECT,
                null,
                PartnerEligibilityReviewReason.NO_ELIGIBLE_CURRENT_EMPLOYEE
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> decide(start, approve));
            Future<String> second = executor.submit(() -> decide(start, reject));
            start.await(5, TimeUnit.SECONDS);

            List<String> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream()
                    .filter("PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED"::equals).count());
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_eligibility_reviews WHERE id = ? AND status IN ('APPROVED', 'REJECTED')",
                Integer.class,
                REVIEW_ID
        ));
        Integer links = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id = ? AND partner_company_id = ?",
                Integer.class,
                CUSTOMER_ID,
                COMPANY_ID
        );
        assertTrue(links == 0 || links == 1);
        verify(auditPublisher, times(1)).publish(any());
    }

    @Test
    void currentReviewDiscoverySelectsLatestPerCompanyWithoutResurrectingSupersededOrStaleHistory() {
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        String priorMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString();
        insertCustomer(OTHER_CUSTOMER_ID, "CUS-REVIEW-OTHER");
        insertCompany(SECOND_COMPANY_ID, "REVIEW-SECOND");
        insertCompany(THIRD_COMPANY_ID, "REVIEW-THIRD");

        jdbcTemplate.update("""
                UPDATE partner_eligibility_reviews
                SET status = 'REJECTED', decision_outcome = 'MANUAL_REVIEW_REJECTED',
                    decision_reason = 'NO_ELIGIBLE_CURRENT_EMPLOYEE',
                    reviewer_user_id = UUID '60000000-0000-4000-8000-000000000001',
                    reviewed_at = TIMESTAMP '2026-09-24 08:30:00',
                    created_at = TIMESTAMP '2026-09-24 08:00:00',
                    updated_at = TIMESTAMP '2026-09-24 08:30:00'
                WHERE id = ?
                """, REVIEW_ID);
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000009"),
                CUSTOMER_ID, COMPANY_ID, month, "SUPERSEDED", null,
                "2026-09-24 09:00:00"
        );

        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000010"),
                CUSTOMER_ID, SECOND_COMPANY_ID, month, "PENDING", null,
                "2026-09-24 10:00:00"
        );
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000011"),
                CUSTOMER_ID, SECOND_COMPANY_ID, month, "REJECTED", "MANUAL_REVIEW_REJECTED",
                "2026-09-24 10:00:00"
        );

        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000020"),
                CUSTOMER_ID, THIRD_COMPANY_ID, priorMonth, "REJECTED", "MANUAL_REVIEW_REJECTED",
                "2026-09-24 11:00:00"
        );
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000021"),
                CUSTOMER_ID, THIRD_COMPANY_ID, month, "PENDING", null,
                "2026-09-24 08:00:00"
        );
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000030"),
                OTHER_CUSTOMER_ID, SECOND_COMPANY_ID, month, "PENDING", null,
                "2026-09-24 12:00:00"
        );

        var selected = reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, month);

        assertEquals(2, selected.size());
        assertEquals(SECOND_COMPANY_ID, selected.get(0).partnerCompanyId());
        assertEquals("MANUAL_REVIEW_REJECTED", selected.get(0).decisionOutcome().name());
        assertEquals(THIRD_COMPANY_ID, selected.get(1).partnerCompanyId());
        assertTrue(selected.get(1).isPending());
        assertTrue(selected.stream().noneMatch(review -> review.partnerCompanyId().equals(COMPANY_ID)));
        assertTrue(reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(OTHER_CUSTOMER_ID, month).stream()
                .allMatch(review -> review.customerId().equals(OTHER_CUSTOMER_ID)));
    }

    @Test
    void multipleCurrentMonthPendingCompanyReviewsRemainSeparateAndBlockCurrentEligibility() {
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        insertCompany(SECOND_COMPANY_ID, "REVIEW-MULTI-SECOND");
        insertCompany(THIRD_COMPANY_ID, "REVIEW-MULTI-THIRD");
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000040"),
                CUSTOMER_ID, SECOND_COMPANY_ID, month, "PENDING", null,
                "2026-09-24 10:00:00"
        );
        insertReview(
                UUID.fromString("10000000-0000-4000-8000-000000000041"),
                CUSTOMER_ID, THIRD_COMPANY_ID, month, "PENDING", null,
                "2026-09-24 11:00:00"
        );

        assertTrue(reviews.existsPendingByCustomerIdAndEffectiveMonth(CUSTOMER_ID, month));
        assertEquals(3, reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(CUSTOMER_ID, month).size());
    }

    private void insertCustomer(UUID customerId, String customerNumber) {
        jdbcTemplate.update("""
                INSERT INTO customers (
                    id, customer_number, status, verification_status, profile_completion_status
                ) VALUES (?, ?, 'ACTIVE', 'VERIFIED', 'COMPLETE')
                """, customerId, customerNumber);
    }

    private void insertCompany(UUID companyId, String companyCode) {
        jdbcTemplate.update("""
                INSERT INTO partner_companies (
                    id, company_code, name, status, salary_advance_policy_limit
                ) VALUES (?, ?, 'Review Discovery Partner', 'ACTIVE', 20000000)
                """, companyId, companyCode);
    }

    private void insertReview(
            UUID reviewId,
            UUID customerId,
            UUID companyId,
            String effectiveMonth,
            String status,
            String decisionOutcome,
            String createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO partner_eligibility_reviews (
                    id, customer_id, partner_company_id, effective_month,
                    trigger_outcome, requested_employee_code, status,
                    decision_outcome, decision_reason, reviewer_user_id, reviewed_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'NOT_FOUND', 'EMP-DISCOVERY', ?, ?,
                    CASE WHEN ? = 'REJECTED' THEN 'NO_ELIGIBLE_CURRENT_EMPLOYEE' ELSE NULL END,
                    CASE WHEN ? = 'REJECTED'
                        THEN UUID '60000000-0000-4000-8000-000000000001' ELSE NULL END,
                    CASE WHEN ? = 'REJECTED' THEN CAST(? AS timestamp) ELSE NULL END,
                    CAST(? AS timestamp), CAST(? AS timestamp))
                """,
                reviewId, customerId, companyId, effectiveMonth, status, decisionOutcome,
                status, status, status, createdAt, createdAt, createdAt
        );
    }

    private String decide(CyclicBarrier start, PartnerEligibilityReviewDecisionRequest request) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            decisions.decide(REVIEW_ID, request);
            return "SUCCESS";
        } catch (BusinessStateConflictException exception) {
            return exception.getErrorCode();
        }
    }
}

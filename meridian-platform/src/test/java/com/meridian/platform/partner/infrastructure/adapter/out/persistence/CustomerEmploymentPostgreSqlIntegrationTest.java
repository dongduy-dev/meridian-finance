package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.port.in.DecidePartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewDecision;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class CustomerEmploymentPostgreSqlIntegrationTest {

    private static final String SCHEMA = "customer_employment_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String IDENTITY_REFERENCE = "FICTIONAL-IDENTITY-EMPLOYMENT-001";

    @Autowired VerifyPartnerEmployeeUseCase verifications;
    @Autowired DecidePartnerEligibilityReviewUseCase decisions;
    @Autowired ImportPartnerEmployeesUseCase imports;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean CustomerIdentityEvidencePort identityEvidence;
    @MockitoBean BusinessAuditPublisher auditPublisher;

    private final ThreadLocal<AuthenticatedUser> actor = new ThreadLocal<>();

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
    void configureActorLookup() {
        when(currentUserProvider.currentUser()).thenAnswer(ignored -> actor.get());
    }

    @Test
    void automaticCrossCompanySwitchIsAtomicAndCreatesFreshCurrentEvidence() {
        Fixture fixture = fixtureWithCurrentEmployer();

        var result = asCustomer(fixture.customerId(), () -> verifications.verifyPartnerEmployee(
                fixture.companyB().companyId(),
                new PartnerEmployeeVerificationRequest(fixture.companyB().employeeCode())
        ));

        assertEquals("MATCHED_ACTIVE", result.outcome());
        assertEquals("DISABLED", linkStatus(fixture.currentLinkId()));
        UUID currentLinkId = currentLinkId(fixture.customerId());
        assertNotEquals(fixture.currentLinkId(), currentLinkId);
        assertEquals(fixture.companyB().companyId(), partnerCompanyId(currentLinkId));
        assertEquals(1, currentLinkCount(fixture.customerId()));
    }

    @Test
    void employmentSwitchDoesNotTransferOrMutateExistingSalaryAdvanceLimit() {
        Fixture fixture = fixtureWithCurrentEmployer();
        UUID limitId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO salary_advance_limits (
                    id, customer_id, customer_partner_employee_link_id,
                    total_limit, used_amount, reserved_amount, available_amount,
                    status, last_refreshed_at
                ) VALUES (?, ?, ?, 10000000, 2000000, 1000000, 7000000,
                    'ACTIVE', CURRENT_TIMESTAMP)
                """, limitId, fixture.customerId(), fixture.currentLinkId());

        asCustomer(fixture.customerId(), () -> verifications.verifyPartnerEmployee(
                fixture.companyB().companyId(),
                new PartnerEmployeeVerificationRequest(fixture.companyB().employeeCode())
        ));

        assertEquals(fixture.currentLinkId(), jdbcTemplate.queryForObject(
                "SELECT customer_partner_employee_link_id FROM salary_advance_limits WHERE id = ?",
                UUID.class,
                limitId
        ));
        assertEquals(new BigDecimal("2000000.00"), jdbcTemplate.queryForObject(
                "SELECT used_amount FROM salary_advance_limits WHERE id = ?",
                BigDecimal.class,
                limitId
        ));
        assertEquals(new BigDecimal("1000000.00"), jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM salary_advance_limits WHERE id = ?",
                BigDecimal.class,
                limitId
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM salary_advance_limits WHERE customer_partner_employee_link_id = ?",
                Integer.class,
                currentLinkId(fixture.customerId())
        ));
    }

    @Test
    void failedNewLinkInsertRollsBackOldLinkDisable() {
        Fixture fixture = fixtureWithCurrentEmployer();
        String functionName = "fail_employment_switch_insert";
        String triggerName = "trg_fail_employment_switch_insert";
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_employment_switch_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced employment switch failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_employment_switch_insert
                BEFORE INSERT ON customer_partner_employee_links
                FOR EACH ROW
                WHEN (NEW.partner_company_id = '%s'::uuid)
                EXECUTE FUNCTION fail_employment_switch_insert()
                """.formatted(fixture.companyB().companyId()));

        try {
            assertThrows(RuntimeException.class, () -> asCustomer(
                    fixture.customerId(),
                    () -> verifications.verifyPartnerEmployee(
                            fixture.companyB().companyId(),
                            new PartnerEmployeeVerificationRequest(fixture.companyB().employeeCode())
                    )
            ));

            assertEquals("VERIFIED", linkStatus(fixture.currentLinkId()));
            assertEquals(fixture.currentLinkId(), currentLinkId(fixture.customerId()));
            assertEquals(0, linkCount(fixture.customerId(), fixture.companyB().companyId()));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName
                    + " ON customer_partner_employee_links");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + functionName + "()");
        }
    }

    @Test
    void concurrentDifferentCompanyVerificationsLeaveExactlyOneCurrentRelationship() throws Exception {
        Fixture fixture = fixtureWithCurrentEmployer();
        Evidence companyC = createCompanyEvidence("C");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> companyB = executor.submit(() -> verifyAfterBarrier(
                    fixture.customerId(), fixture.companyB(), ready, start));
            Future<String> companyCFuture = executor.submit(() -> verifyAfterBarrier(
                    fixture.customerId(), companyC, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals("MATCHED_ACTIVE", companyB.get(10, TimeUnit.SECONDS));
            assertEquals("MATCHED_ACTIVE", companyCFuture.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, currentLinkCount(fixture.customerId()));
        assertEquals(2, disabledLinkCount(fixture.customerId()));
        UUID currentCompany = partnerCompanyId(currentLinkId(fixture.customerId()));
        assertTrue(Set.of(fixture.companyB().companyId(), companyC.companyId()).contains(currentCompany));
    }

    @Test
    void automaticVerificationAndManualApprovalSerializeToOneCurrentRelationship() throws Exception {
        Fixture fixture = fixtureWithCurrentEmployer();
        Evidence companyC = createCompanyEvidence("C");
        UUID reviewId = createPendingReview(fixture.customerId(), fixture.companyB());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> automatic = executor.submit(() -> verifyAfterBarrier(
                    fixture.customerId(), companyC, ready, start));
            Future<String> manual = executor.submit(() -> decideAfterBarrier(
                    reviewId, fixture.companyB().employeeId(), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals("MATCHED_ACTIVE", automatic.get(10, TimeUnit.SECONDS));
            assertEquals("APPROVED", manual.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, currentLinkCount(fixture.customerId()));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM partner_eligibility_reviews WHERE id = ?",
                String.class,
                reviewId
        ));
        UUID currentCompany = partnerCompanyId(currentLinkId(fixture.customerId()));
        assertTrue(Set.of(fixture.companyB().companyId(), companyC.companyId()).contains(currentCompany));
    }

    @Test
    void concurrentImportCannotResurrectRelationshipDisplacedBySwitch() throws Exception {
        Fixture fixture = fixtureWithCurrentEmployer();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ImportPartnerEmployeesRequest request = new ImportPartnerEmployeesRequest(
                UUID.randomUUID(),
                currentMonth(),
                List.of(new PartnerEmployeeImportRowRequest(
                        fixture.companyA().employeeCode(),
                        IDENTITY_REFERENCE,
                        new BigDecimal("14000000.00"),
                        new BigDecimal("4500000.00"),
                        "ACTIVE",
                        true
                ))
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> imported = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return asStaff(() -> imports.importEmployees(
                        fixture.companyA().companyId(), request).status());
            });
            Future<String> switched = executor.submit(() -> verifyAfterBarrier(
                    fixture.customerId(), fixture.companyB(), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals("COMPLETED", imported.get(10, TimeUnit.SECONDS));
            assertEquals("MATCHED_ACTIVE", switched.get(10, TimeUnit.SECONDS));
        }

        assertEquals("DISABLED", linkStatus(fixture.currentLinkId()));
        assertEquals(fixture.companyB().companyId(), partnerCompanyId(currentLinkId(fixture.customerId())));
        assertEquals(1, currentLinkCount(fixture.customerId()));
    }

    private Fixture fixtureWithCurrentEmployer() {
        UUID customerId = UUID.randomUUID();
        createCustomer(customerId);
        when(identityEvidence.findIdentityEvidenceByCustomerId(customerId)).thenReturn(
                java.util.Optional.of(new CustomerIdentityEvidenceSnapshot(
                        customerId, true, true, IDENTITY_REFERENCE
                ))
        );
        Evidence companyA = createCompanyEvidence("A");
        Evidence companyB = createCompanyEvidence("B");
        UUID currentLinkId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer_partner_employee_links (
                    id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id,
                    verification_outcome, link_status, verified_identity_ref, verified_employee_code,
                    last_verified_at, last_refreshed_at
                ) VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                currentLinkId, customerId, companyA.companyId(), companyA.employeeId(), companyA.batchId(),
                IDENTITY_REFERENCE, companyA.employeeCode()
        );
        return new Fixture(customerId, currentLinkId, companyA, companyB);
    }

    private Evidence createCompanyEvidence(String suffix) {
        UUID companyId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        String employeeCode = "EMP-" + suffix + "-" + companyId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO partner_companies (
                    id, company_code, name, status, salary_advance_policy_limit
                ) VALUES (?, ?, ?, 'ACTIVE', 20000000)
                """, companyId, "COMPANY-" + companyId, "Employment Partner " + suffix);
        jdbcTemplate.update("""
                INSERT INTO partner_employee_import_batches (
                    id, partner_company_id, effective_month, status, valid_row_count, invalid_row_count
                ) VALUES (?, ?, ?, 'COMPLETED', 1, 0)
                """, batchId, companyId, currentMonth());
        jdbcTemplate.update("""
                INSERT INTO partner_employees (
                    id, partner_company_id, import_batch_id, employee_code, identity_reference,
                    salary_amount, salary_advance_limit, employment_status, active
                ) VALUES (?, ?, ?, ?, ?, 12000000, 4000000, 'ACTIVE', TRUE)
                """, employeeId, companyId, batchId, employeeCode, IDENTITY_REFERENCE);
        return new Evidence(companyId, batchId, employeeId, employeeCode);
    }

    private UUID createPendingReview(UUID customerId, Evidence evidence) {
        UUID reviewId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO partner_eligibility_reviews (
                    id, customer_id, partner_company_id, effective_month, source_import_batch_id,
                    trigger_outcome, requested_employee_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'NOT_FOUND', ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                reviewId, customerId, evidence.companyId(), currentMonth(), evidence.batchId(),
                evidence.employeeCode()
        );
        return reviewId;
    }

    private void createCustomer(UUID customerId) {
        jdbcTemplate.update("""
                INSERT INTO customers (
                    id, customer_number, status, verification_status, profile_completion_status
                ) VALUES (?, ?, 'ACTIVE', 'VERIFIED', 'COMPLETE')
                """, customerId, "CUS-" + customerId);
    }

    private String verifyAfterBarrier(
            UUID customerId,
            Evidence evidence,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return asCustomer(customerId, () -> verifications.verifyPartnerEmployee(
                evidence.companyId(), new PartnerEmployeeVerificationRequest(evidence.employeeCode())
        ).outcome());
    }

    private String decideAfterBarrier(
            UUID reviewId,
            UUID employeeId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return asStaff(() -> decisions.decide(
                reviewId,
                new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.APPROVE,
                        employeeId,
                        PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                )
        ).status());
    }

    private <T> T asCustomer(UUID customerId, CheckedSupplier<T> action) {
        return asActor(new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.local", "CUSTOMER", customerId,
                Set.of(), Set.of("partner:employee:verify:own")
        ), action);
    }

    private <T> T asStaff(CheckedSupplier<T> action) {
        return asActor(new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:manage")
        ), action);
    }

    private <T> T asActor(AuthenticatedUser authenticatedUser, CheckedSupplier<T> action) {
        actor.set(authenticatedUser);
        try {
            return action.get();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            actor.remove();
        }
    }

    private String currentMonth() {
        return YearMonth.now(clock).toString();
    }

    private String linkStatus(UUID linkId) {
        return jdbcTemplate.queryForObject(
                "SELECT link_status FROM customer_partner_employee_links WHERE id = ?",
                String.class,
                linkId
        );
    }

    private UUID currentLinkId(UUID customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM customer_partner_employee_links WHERE customer_id = ? AND link_status = 'VERIFIED'",
                UUID.class,
                customerId
        );
    }

    private UUID partnerCompanyId(UUID linkId) {
        return jdbcTemplate.queryForObject(
                "SELECT partner_company_id FROM customer_partner_employee_links WHERE id = ?",
                UUID.class,
                linkId
        );
    }

    private int currentLinkCount(UUID customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_partner_employee_links "
                        + "WHERE customer_id = ? AND link_status = 'VERIFIED'",
                Integer.class,
                customerId
        );
    }

    private int disabledLinkCount(UUID customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_partner_employee_links "
                        + "WHERE customer_id = ? AND link_status = 'DISABLED'",
                Integer.class,
                customerId
        );
    }

    private int linkCount(UUID customerId, UUID companyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_partner_employee_links "
                        + "WHERE customer_id = ? AND partner_company_id = ?",
                Integer.class,
                customerId,
                companyId
        );
    }

    private record Fixture(
            UUID customerId,
            UUID currentLinkId,
            Evidence companyA,
            Evidence companyB
    ) {
    }

    private record Evidence(
            UUID companyId,
            UUID batchId,
            UUID employeeId,
            String employeeCode
    ) {
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

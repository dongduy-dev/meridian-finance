package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
import com.meridian.platform.partner.application.port.in.QueryCustomerPartnerEmployeeLinkUseCase;
import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PartnerEmployeeImportPostgreSqlIntegrationTest {

    private static final String SCHEMA = "partner_import_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private ImportPartnerEmployeesUseCase imports;

    @Autowired
    private QueryCustomerPartnerEmployeeLinkUseCase eligibility;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private BusinessAuditPublisher auditPublisher;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void concurrentExactReplayCreatesOneCommittedBusinessEffect() throws Exception {
        UUID companyId = createCompany("ACTIVE");
        UUID requestId = UUID.randomUUID();
        ImportPartnerEmployeesRequest request = request(
                requestId, currentMonth(), "CONCURRENT-001", "FICTIONAL-IDENTITY-CONCURRENT-001", true
        );
        when(currentUserProvider.currentUser()).thenReturn(actor());
        CyclicBarrier start = new CyclicBarrier(3);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<PartnerEmployeeImportResultDto> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return imports.importEmployees(companyId, request);
            });
            Future<PartnerEmployeeImportResultDto> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return imports.importEmployees(companyId, request);
            });
            start.await(5, TimeUnit.SECONDS);

            PartnerEmployeeImportResultDto firstResult = first.get(10, TimeUnit.SECONDS);
            PartnerEmployeeImportResultDto secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(firstResult, secondResult);
            assertEquals(1, count("partner_employee_import_batches", "request_id", requestId));
            assertEquals(1, count("partner_employees", "import_batch_id", firstResult.importBatchId()));
            verify(auditPublisher, times(1)).publish(any());
        }
    }

    @Test
    void authoritativeCurrentMonthImportRefreshesExistingLinkAndRestoresEligibility() {
        UUID companyId = createCompany("ACTIVE");
        UUID customerId = seededCustomerId();
        UUID linkId = UUID.randomUUID();
        String employeeCode = "REFRESH-" + linkId.toString().substring(0, 8);
        String identityReference = "IDENTITY-" + linkId;
        when(currentUserProvider.currentUser()).thenReturn(actor());

        PartnerEmployeeImportResultDto oldBatch = imports.importEmployees(
                companyId,
                request(UUID.randomUUID(), previousMonth(), employeeCode, identityReference, true)
        );
        UUID oldEmployeeId = employeeId(oldBatch.importBatchId(), employeeCode);
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 15, 7, 30);
        insertVerifiedLink(
                linkId, customerId, companyId, oldEmployeeId, oldBatch.importBatchId(),
                identityReference, employeeCode, verifiedAt
        );

        PartnerEmployeeImportResultDto currentBatch = imports.importEmployees(
                companyId,
                request(UUID.randomUUID(), currentMonth(), employeeCode, identityReference, true)
        );
        UUID currentEmployeeId = employeeId(currentBatch.importBatchId(), employeeCode);

        assertEquals(linkId, jdbcTemplate.queryForObject(
                "SELECT id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
        assertEquals(currentEmployeeId, jdbcTemplate.queryForObject(
                "SELECT partner_employee_id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
        assertEquals(currentBatch.importBatchId(), jdbcTemplate.queryForObject(
                "SELECT source_import_batch_id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
        assertEquals(verifiedAt, jdbcTemplate.queryForObject(
                "SELECT last_verified_at FROM customer_partner_employee_links WHERE id = ?",
                LocalDateTime.class,
                linkId
        ));
        assertEquals(
                CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE,
                eligibility.inspectEligibility(customerId, linkId).status()
        );
    }

    @Test
    void authoritativeCurrentMonthImportWithoutSafeMatchLeavesLinkStale() {
        UUID companyId = createCompany("ACTIVE");
        UUID customerId = seededCustomerId();
        UUID linkId = UUID.randomUUID();
        String oldEmployeeCode = "STALE-" + linkId.toString().substring(0, 8);
        String identityReference = "IDENTITY-" + linkId;
        when(currentUserProvider.currentUser()).thenReturn(actor());

        PartnerEmployeeImportResultDto oldBatch = imports.importEmployees(
                companyId,
                request(UUID.randomUUID(), previousMonth(), oldEmployeeCode, identityReference, true)
        );
        UUID oldEmployeeId = employeeId(oldBatch.importBatchId(), oldEmployeeCode);
        insertVerifiedLink(
                linkId, customerId, companyId, oldEmployeeId, oldBatch.importBatchId(),
                identityReference, oldEmployeeCode, LocalDateTime.of(2026, 8, 15, 7, 30)
        );

        imports.importEmployees(
                companyId,
                request(
                        UUID.randomUUID(), currentMonth(), "OTHER-" + oldEmployeeCode,
                        "OTHER-" + identityReference, true
                )
        );

        assertEquals(oldBatch.importBatchId(), jdbcTemplate.queryForObject(
                "SELECT source_import_batch_id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
        assertEquals(
                CustomerPartnerEmployeeEligibilityDto.Status.EVIDENCE_STALE,
                eligibility.inspectEligibility(customerId, linkId).status()
        );
    }

    @Test
    void auditFailureRollsBackBatchEmployeesAndLinkRefresh() {
        UUID companyId = createCompany("ACTIVE");
        UUID customerId = seededCustomerId();
        UUID linkId = UUID.randomUUID();
        String employeeCode = "ROLLBACK-" + linkId.toString().substring(0, 8);
        String identityReference = "IDENTITY-" + linkId;
        UUID requestId = UUID.randomUUID();
        when(currentUserProvider.currentUser()).thenReturn(actor());
        PartnerEmployeeImportResultDto oldBatch = imports.importEmployees(
                companyId,
                request(UUID.randomUUID(), previousMonth(), employeeCode, identityReference, true)
        );
        UUID oldEmployeeId = employeeId(oldBatch.importBatchId(), employeeCode);
        insertVerifiedLink(
                linkId, customerId, companyId, oldEmployeeId, oldBatch.importBatchId(),
                identityReference, employeeCode, LocalDateTime.of(2026, 8, 15, 7, 30)
        );
        doThrow(new IllegalStateException("audit unavailable")).when(auditPublisher).publish(any());

        assertThrows(IllegalStateException.class, () ->
                imports.importEmployees(
                        companyId,
                        request(requestId, currentMonth(), employeeCode, identityReference, true)
                ));

        assertEquals(0, count("partner_employee_import_batches", "request_id", requestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_employees WHERE partner_company_id = ? AND employee_code = ?",
                Integer.class,
                companyId,
                employeeCode
        ));
        assertEquals(oldEmployeeId, jdbcTemplate.queryForObject(
                "SELECT partner_employee_id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
        assertEquals(oldBatch.importBatchId(), jdbcTemplate.queryForObject(
                "SELECT source_import_batch_id FROM customer_partner_employee_links WHERE id = ?", UUID.class, linkId
        ));
    }

    private int count(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }

    private UUID createCompany(String status) {
        UUID companyId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO partner_companies "
                        + "(id, company_code, name, status, salary_advance_policy_limit) "
                        + "VALUES (?, ?, ?, ?, ?)",
                companyId,
                "TEST-" + companyId,
                "Test Partner " + companyId,
                status,
                new BigDecimal("10000000.00")
        );
        return companyId;
    }

    private UUID employeeId(UUID importBatchId, String employeeCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM partner_employees WHERE import_batch_id = ? AND employee_code = ?",
                UUID.class,
                importBatchId,
                employeeCode
        );
    }

    private UUID seededCustomerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM customers ORDER BY id LIMIT 1",
                UUID.class
        );
    }

    private void insertVerifiedLink(
            UUID linkId,
            UUID customerId,
            UUID companyId,
            UUID employeeId,
            UUID importBatchId,
            String identityReference,
            String employeeCode,
            LocalDateTime verifiedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO customer_partner_employee_links "
                        + "(id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id, "
                        + "verification_outcome, link_status, verified_identity_ref, verified_employee_code, "
                        + "last_verified_at, last_refreshed_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', ?, ?, ?, ?)",
                linkId,
                customerId,
                companyId,
                employeeId,
                importBatchId,
                identityReference,
                employeeCode,
                verifiedAt,
                verifiedAt
        );
    }

    private String currentMonth() {
        return YearMonth.now(clock).toString();
    }

    private String previousMonth() {
        return YearMonth.now(clock).minusMonths(1).toString();
    }

    private static ImportPartnerEmployeesRequest request(
            UUID requestId,
            String effectiveMonth,
            String employeeCode,
            String identityReference,
            boolean active
    ) {
        return new ImportPartnerEmployeesRequest(
                requestId,
                effectiveMonth,
                List.of(new PartnerEmployeeImportRowRequest(
                        employeeCode,
                        identityReference,
                        new BigDecimal("12000000.00"),
                        new BigDecimal("4000000.00"),
                        "ACTIVE",
                        active
                ))
        );
    }

    private static AuthenticatedUser actor() {
        return new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:manage")
        );
    }
}

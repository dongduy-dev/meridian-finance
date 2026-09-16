package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
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
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ImportPartnerEmployeesUseCase imports;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        UUID requestId = UUID.randomUUID();
        ImportPartnerEmployeesRequest request = request(requestId, "CONCURRENT-001");
        when(currentUserProvider.currentUser()).thenReturn(actor());
        CyclicBarrier start = new CyclicBarrier(3);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<PartnerEmployeeImportResultDto> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return imports.importEmployees(COMPANY_ID, request);
            });
            Future<PartnerEmployeeImportResultDto> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return imports.importEmployees(COMPANY_ID, request);
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
    void auditFailureRollsBackBatchAndEmployees() {
        UUID requestId = UUID.randomUUID();
        when(currentUserProvider.currentUser()).thenReturn(actor());
        doThrow(new IllegalStateException("audit unavailable")).when(auditPublisher).publish(any());

        assertThrows(IllegalStateException.class, () ->
                imports.importEmployees(COMPANY_ID, request(requestId, "ROLLBACK-001")));

        assertEquals(0, count("partner_employee_import_batches", "request_id", requestId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_employees WHERE employee_code = 'ROLLBACK-001'",
                Integer.class
        ));
    }

    private int count(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }

    private static ImportPartnerEmployeesRequest request(UUID requestId, String employeeCode) {
        return new ImportPartnerEmployeesRequest(
                requestId,
                "2026-09",
                List.of(new PartnerEmployeeImportRowRequest(
                        employeeCode,
                        "FICTIONAL-IDENTITY-" + employeeCode,
                        new BigDecimal("12000000.00"),
                        new BigDecimal("4000000.00"),
                        "ACTIVE",
                        true
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

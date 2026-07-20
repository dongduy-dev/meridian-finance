package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class StaffCorrectionV24PostgreSqlIntegrationTest {

    private static final String SCHEMA =
            "meridian_staff_v24_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void cleanMigrationReachesV24WithExactRoleGrantsAndStaffScopeConstraint() {
        assertEquals("24", jdbcTemplate.queryForObject(
                "select version from " + SCHEMA
                        + ".flyway_schema_history where success order by installed_rank desc limit 1",
                String.class
        ));

        assertEquals(1, roleGrantCount("LOAN_OFFICER", "loan:correction:staff"));
        assertEquals(1, roleGrantCount("LOAN_OFFICER", "document:waive"));
        assertEquals(1, roleGrantCount("LOAN_OFFICER", "document:review"));
        assertEquals(1, roleGrantCount("BACK_OFFICE_ADMIN", "document:upload:staff"));
        assertEquals(0, roleGrantCount("APPROVER", "document:waive"));
        assertEquals(0, roleGrantCount("ACCOUNTING_OFFICER", "document:waive"));

        String definition = jdbcTemplate.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'chk_loan_correction_tasks_scope_fields'
                  and conrelid = (? || '.loan_correction_tasks')::regclass
                """,
                String.class,
                SCHEMA
        );
        assertTrue(definition.contains("responsible_party"));
        assertTrue(definition.contains("CUSTOMER"));
        assertTrue(definition.contains("STAFF"));
        assertTrue(definition.contains("RECENT_PAYSLIP"));
    }

    private int roleGrantCount(String roleCode, String permissionCode) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from %s.role_permissions rp
                join %s.roles r on r.id = rp.role_id
                join %s.permissions p on p.id = rp.permission_id
                where r.code = ? and p.code = ?
                """.formatted(SCHEMA, SCHEMA, SCHEMA),
                Integer.class,
                roleCode,
                permissionCode
        );
    }
}

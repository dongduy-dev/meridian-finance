package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class ContractReadinessV26PostgreSqlIntegrationTest {

    private static final String SCHEMA = "meridian_contract_perm_v26_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final List<String> CONTRACT_PERMISSIONS = List.of(
            "loan:contract:acknowledge:own",
            "loan:contract:prepare",
            "loan:contract:read",
            "loan:disbursement:prepare"
    );

    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void assignsOnlyCustomerAcknowledgmentAndAccountingPreparationPermissions() {
        assertEquals("26", jdbc.queryForObject(
                "select version from " + SCHEMA
                        + ".flyway_schema_history where success order by installed_rank desc limit 1",
                String.class
        ));
        assertEquals(1, roleGrantCount("CUSTOMER", "loan:contract:acknowledge:own"));
        assertEquals(0, roleGrantCount("CUSTOMER", "loan:contract:prepare"));
        assertEquals(0, roleGrantCount("CUSTOMER", "loan:contract:read"));
        assertEquals(0, roleGrantCount("CUSTOMER", "loan:disbursement:prepare"));

        assertEquals(0, roleGrantCount("ACCOUNTING_OFFICER", "loan:contract:acknowledge:own"));
        assertEquals(1, roleGrantCount("ACCOUNTING_OFFICER", "loan:contract:prepare"));
        assertEquals(1, roleGrantCount("ACCOUNTING_OFFICER", "loan:contract:read"));
        assertEquals(1, roleGrantCount("ACCOUNTING_OFFICER", "loan:disbursement:prepare"));

        for (String role : List.of("LOAN_OFFICER", "APPROVER", "BACK_OFFICE_ADMIN")) {
            for (String permission : CONTRACT_PERMISSIONS) {
                assertEquals(0, roleGrantCount(role, permission));
            }
        }
        assertEquals(1, roleGrantCount("ACCOUNTING_OFFICER", "loan:disburse"));
    }

    private int roleGrantCount(String roleCode, String permissionCode) {
        return jdbc.queryForObject(
                """
                        select count(*)
                        from roles role
                        join role_permissions grant_row on grant_row.role_id = role.id
                        join permissions permission on permission.id = grant_row.permission_id
                        where role.code = ? and permission.code = ?
                        """,
                Integer.class,
                roleCode,
                permissionCode
        );
    }
}

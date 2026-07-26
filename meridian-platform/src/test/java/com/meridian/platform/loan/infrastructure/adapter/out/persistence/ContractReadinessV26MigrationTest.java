package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractReadinessV26MigrationTest {

    @Test
    void seedsOnlyTheApprovedContractReadinessPermissions() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V26__add_contract_readiness_permissions.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("'loan:contract:acknowledge:own'"));
        assertTrue(sql.contains("'loan:contract:prepare'"));
        assertTrue(sql.contains("'loan:contract:read'"));
        assertTrue(sql.contains("'loan:disbursement:prepare'"));
        assertTrue(sql.contains("WHERE role.code = 'CUSTOMER'"));
        assertTrue(sql.contains("WHERE role.code = 'ACCOUNTING_OFFICER'"));
        assertFalse(sql.contains("'loan:disburse'"));
    }
}

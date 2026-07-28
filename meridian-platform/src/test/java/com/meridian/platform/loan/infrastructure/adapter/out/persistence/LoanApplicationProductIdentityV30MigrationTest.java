package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanApplicationProductIdentityV30MigrationTest {

    @Test
    void hardensOnlyLoanApplicationProductIdentity() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V30__harden_loan_application_product_identity.sql"
        ));

        assertTrue(sql.contains("uq_loan_products_identity_tuple"));
        assertTrue(sql.contains("fk_loan_applications_product_identity"));
        assertTrue(sql.contains("reject_loan_application_product_identity_mutation"));
        assertTrue(sql.contains("trg_loan_applications_product_identity_immutable"));
        assertTrue(sql.contains("V30 preflight failed"));
        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("loan_contracts"));
        assertFalse(sql.contains("audit_events"));
        assertFalse(sql.contains("permissions"));
    }
}

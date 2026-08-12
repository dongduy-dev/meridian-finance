package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class UclContractV41PostgreSqlIntegrationTest {

    private static final String SCHEMA = "ucl_contract_v41_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void acceptsSalaryAndMonthlyContractsAndRejectsUnsupportedVocabulary() {
        Seed salary = seed("SALARY_ADVANCE", "SALARY_BASED", "ON_SALARY_DATE");
        Seed ucl = seed(
                "UNSECURED_CONSUMER_LOAN", "UNSECURED", "MONTHLY_INSTALLMENT"
        );
        insertContract(salary, "ON_SALARY_DATE", amount("1000"), amount("100"), amount("1100"));
        insertContract(ucl, "MONTHLY_INSTALLMENT", amount("1000"), amount("100"), amount("1100"));

        Seed unsupported = seed("SALARY_ADVANCE", "SALARY_BASED", "ON_SALARY_DATE");
        assertThrows(DataAccessException.class, () -> insertContract(
                unsupported, "BALLOON_PAYMENT", amount("1000"), amount("100"), amount("1100")
        ));

        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from loan_contracts", Integer.class
        ));
    }

    @Test
    void preservesWholeVndAndExactReconciliationRules() {
        Seed fractional = seed("SALARY_ADVANCE", "SALARY_BASED", "ON_SALARY_DATE");
        assertThrows(DataAccessException.class, () -> insertContract(
                fractional, "ON_SALARY_DATE", new BigDecimal("1000.50"),
                amount("100"), new BigDecimal("1100.50")
        ));

        Seed unreconciled = seed("SALARY_ADVANCE", "SALARY_BASED", "ON_SALARY_DATE");
        assertThrows(DataAccessException.class, () -> insertContract(
                unreconciled, "ON_SALARY_DATE", amount("1000"), amount("100"), amount("1099")
        ));
    }

    @Test
    void fullyMigratedSchemaContainsV41AndTheExactConstraintVocabulary() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA
                        + ".flyway_schema_history where version = '41' and success",
                Integer.class
        ));
        String definition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint "
                        + "where conrelid = 'loan_contracts'::regclass "
                        + "and conname = 'chk_loan_contracts_terms'",
                String.class
        );
        assertTrue(definition.contains("ON_SALARY_DATE"));
        assertTrue(definition.contains("MONTHLY_INSTALLMENT"));
    }

    private Seed seed(String productCode, String productType, String repaymentMethod) {
        UUID customerId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID offerItemId = UUID.randomUUID();
        String token = applicationId.toString().replace("-", "");
        jdbcTemplate.update(
                "insert into customers (id,customer_number,status,verification_status,profile_completion_status) "
                        + "values (?,?,'ACTIVE','UNVERIFIED','COMPLETE')",
                customerId, "V41-" + token.substring(0, 12)
        );
        jdbcTemplate.update(
                "insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,"
                        + "status,primary_account) values (?,?,'TEST','Test Bank','V41 Customer',"
                        + "'protected',?,'5678','ACTIVE',true)",
                bankAccountId, customerId, "v41-bank-" + token
        );
        UUID productId = jdbcTemplate.queryForObject(
                "select id from loan_products where product_code = ?", UUID.class, productCode
        );
        UUID policyId = jdbcTemplate.queryForObject(
                "select id from loan_product_policies where loan_product_id = ? "
                        + "and policy_code = 'DEFAULT_POLICY'",
                UUID.class, productId
        );
        jdbcTemplate.update(
                "insert into loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,product_type,"
                        + "status,requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,?,?,'CONTRACT_PENDING',1000,1,current_timestamp)",
                applicationId, customerId, productId, "V41-APP-" + token,
                productCode, productType
        );
        jdbcTemplate.update(
                "insert into approved_offers "
                        + "(id,loan_application_id,source_loan_product_policy_id,status,approved_principal,"
                        + "approved_term_months,interest_calculation_method,flat_monthly_interest_rate,"
                        + "total_interest,fee_amount,total_repayment_amount,repayment_method,generated_at,"
                        + "expires_at,accepted_at) values (?,?,?,'ACCEPTED',1000,1,"
                        + "'FLAT_ORIGINAL_PRINCIPAL',0.1,100,0,1100,?,current_timestamp-interval '2 day',"
                        + "current_timestamp+interval '5 day',current_timestamp-interval '1 day')",
                offerId, applicationId, policyId, repaymentMethod
        );
        jdbcTemplate.update(
                "insert into approved_offer_repayment_items "
                        + "(id,approved_offer_id,installment_number,principal_due,interest_due,fee_due,total_due) "
                        + "values (?,?,1,1000,100,0,1100)",
                offerItemId, offerId
        );
        return new Seed(customerId, bankAccountId, applicationId, offerId, offerItemId);
    }

    private void insertContract(
            Seed seed,
            String repaymentMethod,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total
    ) {
        transactions.executeWithoutResult(status -> {
            UUID contractId = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into loan_contracts "
                            + "(id,loan_application_id,approved_offer_id,contract_reference,contract_version,"
                            + "status,approved_principal,approved_term_months,interest_calculation_method,"
                            + "flat_monthly_interest_rate,total_interest,fee_amount,total_repayment_amount,"
                            + "repayment_method,customer_id,source_bank_account_id,bank_code,bank_name_snapshot,"
                            + "account_holder_name,account_number_last_four,primary_at_capture,active_at_capture,"
                            + "account_captured_at,protection_scheme,protection_key_id,protection_nonce,"
                            + "protected_account_number,protection_aad_version,preparation_request_id,"
                            + "prepared_by_user_id,prepared_at) values (?,?,?,?,1,'PREPARED',?,1,"
                            + "'FLAT_ORIGINAL_PRINCIPAL',0.1,?,0,?,?,?,?,'TEST','Test Bank','V41 Customer',"
                            + "'5678',true,true,current_timestamp,'AES-256-GCM','v1',decode('01','hex'),"
                            + "decode('02','hex'),'DISBURSEMENT_ACCOUNT_V1',?,?,current_timestamp)",
                    contractId, seed.applicationId, seed.offerId,
                    "MCT-V41-" + contractId, principal, interest, total, repaymentMethod,
                    seed.customerId, seed.bankAccountId, UUID.randomUUID(), ACCOUNTING_USER_ID
            );
            jdbcTemplate.update(
                    "insert into loan_contract_repayment_items "
                            + "(id,loan_contract_id,source_approved_offer_repayment_item_id,installment_number,"
                            + "principal_due,interest_due,fee_due,total_due) values (?,?,?,1,?,?,0,?)",
                    UUID.randomUUID(), contractId, seed.offerItemId, principal, interest, total
            );
        });
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private record Seed(
            UUID customerId,
            UUID bankAccountId,
            UUID applicationId,
            UUID offerId,
            UUID offerItemId
    ) {
    }
}

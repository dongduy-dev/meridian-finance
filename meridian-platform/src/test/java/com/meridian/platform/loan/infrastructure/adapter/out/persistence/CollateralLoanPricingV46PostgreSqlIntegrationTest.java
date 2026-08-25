package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CollateralLoanPricingV46PostgreSqlIntegrationTest {

    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 19, 9, 0);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradesV45PolicyToExecutableCollateralPricing() {
        String schema = schema("upgrade");
        try {
            migrate(schema, "45");
            UUID policyId = policyId(schema);
            assertNull(jdbc.queryForObject(
                    "select flat_monthly_interest_rate from " + schema
                            + ".loan_product_policies where id = ?",
                    BigDecimal.class,
                    policyId
            ));

            migrate(schema, null);

            assertEquals("51", latestVersion(schema));
            assertExecutablePolicy(schema);
        } finally {
            drop(schema);
        }
    }

    @Test
    void cleanMigrationCreatesExecutablePolicyAndPreservesCommonOfferConstraints() {
        String schema = schema("clean");
        try {
            migrate(schema, null);
            jdbc.execute("set search_path to " + schema + ", public");
            assertExecutablePolicy(schema);

            UUID customerId = insertCustomer(schema);
            UUID applicationId = insertApplication(schema, customerId, "CL-V46-");
            UUID policyId = policyId(schema);
            UUID offerId = insertOffer(schema, applicationId, policyId, new BigDecimal("14160000.00"));

            assertEquals("MONTHLY_INSTALLMENT", jdbc.queryForObject(
                    "select repayment_method from " + schema + ".approved_offers where id = ?",
                    String.class,
                    offerId
            ));
            assertThrows(DataAccessException.class, () -> insertOffer(
                    schema, applicationId, policyId, new BigDecimal("14160000.00")
            ));

            UUID invalidApplicationId = insertApplication(schema, insertCustomer(schema), "CL-V46-X-");
            assertThrows(DataAccessException.class, () -> insertOffer(
                    schema, invalidApplicationId, policyId, new BigDecimal("14159999.00")
            ));
        } finally {
            drop(schema);
        }
    }

    private void assertExecutablePolicy(String schema) {
        PolicySnapshot policy = jdbc.queryForObject(
                "select interest_calculation_method, flat_monthly_interest_rate, fee_amount, "
                        + "repayment_method, offer_validity_days from " + schema
                        + ".loan_product_policies where id = ?",
                (resultSet, rowNumber) -> new PolicySnapshot(
                        resultSet.getString("interest_calculation_method"),
                        resultSet.getBigDecimal("flat_monthly_interest_rate"),
                        resultSet.getBigDecimal("fee_amount"),
                        resultSet.getString("repayment_method"),
                        resultSet.getInt("offer_validity_days")
                ),
                policyId(schema)
        );
        List<Integer> terms = jdbc.queryForList(
                "select term_months from " + schema
                        + ".loan_product_policy_terms where loan_product_policy_id = ? order by term_months",
                Integer.class,
                policyId(schema)
        );

        assertEquals("FLAT_ORIGINAL_PRINCIPAL", policy.interestMethod());
        assertEquals(new BigDecimal("0.015000"), policy.monthlyRate());
        assertEquals(new BigDecimal("0.00"), policy.feeAmount());
        assertEquals("MONTHLY_INSTALLMENT", policy.repaymentMethod());
        assertEquals(7, policy.validityDays());
        assertEquals(List.of(6, 12, 18, 24), terms);
    }

    private UUID insertCustomer(String schema) {
        UUID customerId = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + ".customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "values (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId,
                "CL-V46-" + customerId
        );
        return customerId;
    }

    private UUID insertApplication(String schema, UUID customerId, String prefix) {
        UUID applicationId = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + ".loan_applications "
                        + "(id, customer_id, loan_product_id, application_number, product_code, product_type, "
                        + "status, requested_amount, requested_term_months, submitted_at) "
                        + "select ?, ?, product.id, ?, 'COLLATERAL_LOAN', 'SECURED', "
                        + "'CUSTOMER_ACCEPTANCE_PENDING', 12000000.00, 12, ? from " + schema
                        + ".loan_products product where product.product_code = 'COLLATERAL_LOAN'",
                applicationId,
                customerId,
                prefix + applicationId,
                GENERATED_AT.minusDays(1)
        );
        return applicationId;
    }

    private UUID insertOffer(
            String schema,
            UUID applicationId,
            UUID policyId,
            BigDecimal totalRepayment
    ) {
        UUID offerId = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + ".approved_offers "
                        + "(id, loan_application_id, source_loan_product_policy_id, status, approved_principal, "
                        + "approved_term_months, interest_calculation_method, flat_monthly_interest_rate, "
                        + "total_interest, fee_amount, total_repayment_amount, repayment_method, "
                        + "generated_at, expires_at) values (?, ?, ?, 'PENDING', 12000000.00, 12, "
                        + "'FLAT_ORIGINAL_PRINCIPAL', 0.015000, 2160000.00, 0.00, ?, "
                        + "'MONTHLY_INSTALLMENT', ?, ?)",
                offerId,
                applicationId,
                policyId,
                totalRepayment,
                GENERATED_AT,
                GENERATED_AT.plusDays(7)
        );
        return offerId;
    }

    private UUID policyId(String schema) {
        return jdbc.queryForObject(
                "select policy.id from " + schema + ".loan_product_policies policy join " + schema
                        + ".loan_products product on product.id = policy.loan_product_id "
                        + "where product.product_code = 'COLLATERAL_LOAN' "
                        + "and policy.policy_code = 'DEFAULT_POLICY' and policy.active",
                UUID.class
        );
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success order by installed_rank desc limit 1",
                String.class
        );
    }

    private String schema(String suffix) {
        return "mer_cl_v46_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private record PolicySnapshot(
            String interestMethod,
            BigDecimal monthlyRate,
            BigDecimal feeAmount,
            String repaymentMethod,
            int validityDays
    ) {
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class UclPricingV40PostgreSqlIntegrationTest {

    private static final String SCHEMA = "ucl_pricing_v40_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 11, 9, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void seedsExecutableUclDefaultPolicyAndExactTerms() {
        PolicySnapshot policy = jdbcTemplate.queryForObject(
                """
                        select policy.interest_calculation_method,
                               policy.flat_monthly_interest_rate,
                               policy.fee_amount,
                               policy.repayment_method,
                               policy.offer_validity_days
                        from loan_product_policies policy
                        join loan_products product on product.id = policy.loan_product_id
                        where product.product_code = 'UNSECURED_CONSUMER_LOAN'
                          and policy.policy_code = 'DEFAULT_POLICY'
                          and policy.active
                        """,
                (resultSet, rowNumber) -> new PolicySnapshot(
                        resultSet.getString("interest_calculation_method"),
                        resultSet.getBigDecimal("flat_monthly_interest_rate"),
                        resultSet.getBigDecimal("fee_amount"),
                        resultSet.getString("repayment_method"),
                        resultSet.getInt("offer_validity_days")
                )
        );
        UUID policyId = policyId("UNSECURED_CONSUMER_LOAN");
        List<Integer> terms = jdbcTemplate.queryForList(
                "select term_months from loan_product_policy_terms "
                        + "where loan_product_policy_id = ? order by term_months",
                Integer.class,
                policyId
        );

        assertEquals("FLAT_ORIGINAL_PRINCIPAL", policy.interestMethod());
        assertEquals(new BigDecimal("0.018000"), policy.monthlyRate());
        assertEquals(new BigDecimal("0.00"), policy.feeAmount());
        assertEquals("MONTHLY_INSTALLMENT", policy.repaymentMethod());
        assertEquals(7, policy.validityDays());
        assertEquals(List.of(3, 6, 9, 12), terms);
    }

    @Test
    void acceptsBothExecutableRepaymentMethodsAndRejectsUnsupportedVocabulary() {
        UUID salaryApplicationId = insertApplication(
                insertCustomer(), "SALARY_ADVANCE", "SALARY_BASED", 1, "SA-V40-"
        );
        UUID uclApplicationId = insertApplication(
                insertCustomer(), "UNSECURED_CONSUMER_LOAN", "UNSECURED", 6, "UCL-V40-"
        );
        UUID invalidApplicationId = insertApplication(
                insertCustomer(), "UNSECURED_CONSUMER_LOAN", "UNSECURED", 6, "UCL-V40-X-"
        );

        insertOffer(
                salaryApplicationId,
                policyId("SALARY_ADVANCE"),
                "ON_SALARY_DATE",
                1,
                new BigDecimal("3000000.00"),
                new BigDecimal("36000.00")
        );
        insertOffer(
                uclApplicationId,
                policyId("UNSECURED_CONSUMER_LOAN"),
                "MONTHLY_INSTALLMENT",
                6,
                new BigDecimal("5000000.00"),
                new BigDecimal("540000.00")
        );

        assertThrows(DataAccessException.class, () -> insertOffer(
                invalidApplicationId,
                policyId("UNSECURED_CONSUMER_LOAN"),
                "BALLOON_PAYMENT",
                6,
                new BigDecimal("5000000.00"),
                new BigDecimal("540000.00")
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from approved_offers",
                Integer.class
        ));
    }

    private UUID insertCustomer() {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "values (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId,
                "UCL-V40-" + customerId
        );
        return customerId;
    }

    private UUID insertApplication(
            UUID customerId,
            String productCode,
            String productType,
            int termMonths,
            String applicationPrefix
    ) {
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into loan_applications (
                            id, customer_id, loan_product_id, application_number,
                            product_code, product_type, status, requested_amount,
                            requested_term_months, submitted_at
                        )
                        select ?, ?, product.id, ?, ?, ?, 'CUSTOMER_ACCEPTANCE_PENDING',
                               5000000.00, ?, ?
                        from loan_products product where product.product_code = ?
                        """,
                applicationId,
                customerId,
                applicationPrefix + applicationId,
                productCode,
                productType,
                termMonths,
                GENERATED_AT.minusDays(1),
                productCode
        );
        return applicationId;
    }

    private void insertOffer(
            UUID applicationId,
            UUID policyId,
            String repaymentMethod,
            int termMonths,
            BigDecimal principal,
            BigDecimal interest
    ) {
        jdbcTemplate.update(
                """
                        insert into approved_offers (
                            id, loan_application_id, source_loan_product_policy_id, status,
                            approved_principal, approved_term_months, interest_calculation_method,
                            flat_monthly_interest_rate, total_interest, fee_amount,
                            total_repayment_amount, repayment_method, generated_at, expires_at
                        ) values (?, ?, ?, 'PENDING', ?, ?, 'FLAT_ORIGINAL_PRINCIPAL',
                                  0.018000, ?, 0.00, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                applicationId,
                policyId,
                principal,
                termMonths,
                interest,
                principal.add(interest),
                repaymentMethod,
                GENERATED_AT,
                GENERATED_AT.plusDays(7)
        );
    }

    private UUID policyId(String productCode) {
        return jdbcTemplate.queryForObject(
                "select policy.id from loan_product_policies policy "
                        + "join loan_products product on product.id = policy.loan_product_id "
                        + "where product.product_code = ? and policy.policy_code = 'DEFAULT_POLICY'",
                UUID.class,
                productCode
        );
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

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
class SalaryAdvanceCriticalPathV21PostgreSqlIntegrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void v21MigratesWholeVndRowsAndConstraintRejectsFractions() {
        String schema = schemaName("whole");
        try {
            migrateTo(schema, "20");
            UUID applicationId = insertLoanApplication(schema, new BigDecimal("3000000.00"));

            migrateLatest(schema);
            assertMigrationSucceeded(schema, "21");
            assertEquals(1, jdbcTemplate.queryForObject(
                    """
                            SELECT count(*)
                            FROM information_schema.table_constraints
                            WHERE constraint_schema = ?
                              AND table_name = 'loan_applications'
                              AND constraint_name = 'chk_loan_applications_requested_amount_whole_vnd'
                            """,
                    Integer.class,
                    schema
            ));

            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".loan_applications SET requested_amount = ? WHERE id = ?",
                    new BigDecimal("3000000.50"),
                    applicationId
            ));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void v21FailsClearlyInsteadOfRoundingExistingFractionalRows() {
        String schema = schemaName("fraction");
        try {
            migrateTo(schema, "20");
            insertLoanApplication(schema, new BigDecimal("3000000.50"));

            FlywayException exception = assertThrows(FlywayException.class, () -> migrateLatest(schema));

            assertTrue(allMessages(exception).contains(
                    "Cannot enforce whole-VND loan application amounts because existing requested_amount values "
                            + "contain non-zero fractional VND"
            ));
            assertEquals(0, jdbcTemplate.queryForObject(
                    """
                            SELECT count(*)
                            FROM information_schema.table_constraints
                            WHERE constraint_schema = ?
                              AND table_name = 'loan_applications'
                              AND constraint_name = 'chk_loan_applications_requested_amount_whole_vnd'
                            """,
                    Integer.class,
                    schema
            ));
        } finally {
            dropSchema(schema);
        }
    }

    private void migrateTo(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private void migrateLatest(String schema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void assertMigrationSucceeded(String schema, String version) {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + schema + ".flyway_schema_history WHERE version = ? AND success",
                Integer.class,
                version
        ));
    }

    private UUID insertLoanApplication(String schema, BigDecimal requestedAmount) {
        UUID customerId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".customers ORDER BY customer_number LIMIT 1",
                UUID.class
        );
        UUID loanProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
        UUID applicationId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        INSERT INTO %s.loan_applications (
                            id,
                            customer_id,
                            loan_product_id,
                            application_number,
                            product_code,
                            product_type,
                            status,
                            requested_amount,
                            requested_term_months,
                            submitted_at
                        ) VALUES (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'SUBMITTED', ?, 1, CURRENT_TIMESTAMP)
                        """.formatted(schema),
                applicationId,
                customerId,
                loanProductId,
                "SA-V21-" + UUID.randomUUID(),
                requestedAmount
        );
        return applicationId;
    }

    private void dropSchema(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }

    private static String schemaName(String suffix) {
        return "meridian_v21_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}

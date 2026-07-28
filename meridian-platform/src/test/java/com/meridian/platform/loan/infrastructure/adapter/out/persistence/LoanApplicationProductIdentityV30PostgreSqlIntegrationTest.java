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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class LoanApplicationProductIdentityV30PostgreSqlIntegrationTest {

    private static final String SCHEMA = schemaName("installed");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void cleanV1ThroughV30AndV29UpgradeAcceptValidIdentity() {
        assertEquals("30", latestVersion(SCHEMA));
        UUID installedApplication = insertValidApplication(SCHEMA);
        assertTrue(applicationExists(SCHEMA, installedApplication));

        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "29");
            UUID applicationId = insertValidApplication(schema);

            migrateLatest(schema);

            assertEquals("30", latestVersion(schema));
            assertTrue(applicationExists(schema, applicationId));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void preflightRejectsMismatchedProductCode() {
        assertPreflightFailure((schema, applicationId) -> jdbc.update(
                "update " + schema
                        + ".loan_applications set product_code = 'UNSECURED_CONSUMER_LOAN' "
                        + "where id = ?",
                applicationId
        ));
    }

    @Test
    void preflightRejectsMismatchedProductType() {
        assertPreflightFailure((schema, applicationId) -> jdbc.update(
                "update " + schema
                        + ".loan_applications set product_type = 'UNSECURED' where id = ?",
                applicationId
        ));
    }

    @Test
    void preflightRejectsMissingProductId() {
        assertPreflightFailure((schema, applicationId) -> {
            jdbc.execute("alter table " + schema
                    + ".loan_applications drop constraint fk_loan_applications_loan_product");
            jdbc.update("update " + schema
                            + ".loan_applications set loan_product_id = ? where id = ?",
                    UUID.randomUUID(),
                    applicationId
            );
        });
    }

    @Test
    void rejectsMismatchedNewTupleAndEveryDirectProductMutation() {
        UUID applicationId = insertValidApplication(SCHEMA);
        Map<String, Object> ucl = product(SCHEMA, "UNSECURED_CONSUMER_LOAN");

        assertThrows(DataAccessException.class, () -> insertApplication(
                SCHEMA,
                UUID.randomUUID(),
                (UUID) product(SCHEMA, "SALARY_ADVANCE").get("id"),
                "UNSECURED_CONSUMER_LOAN",
                "UNSECURED"
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update " + SCHEMA + ".loan_applications set loan_product_id = ? where id = ?",
                ucl.get("id"),
                applicationId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update " + SCHEMA
                        + ".loan_applications set product_code = 'UNSECURED_CONSUMER_LOAN' "
                        + "where id = ?",
                applicationId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update " + SCHEMA
                        + ".loan_applications set product_type = 'UNSECURED' where id = ?",
                applicationId
        ));
    }

    @Test
    void ordinaryWorkflowUpdatesRemainAllowedAndReferencedParentCannotDrift() {
        UUID applicationId = insertValidApplication(SCHEMA);
        UUID productId = (UUID) product(SCHEMA, "SALARY_ADVANCE").get("id");

        assertEquals(1, jdbc.update(
                "update " + SCHEMA
                        + ".loan_applications set status = 'CANCELLED', updated_at = ? where id = ?",
                LocalDateTime.of(2026, 7, 28, 12, 0),
                applicationId
        ));
        assertEquals("CANCELLED", jdbc.queryForObject(
                "select status from " + SCHEMA + ".loan_applications where id = ?",
                String.class,
                applicationId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update " + SCHEMA
                        + ".loan_products set product_type = 'UNSECURED' where id = ?",
                productId
        ));
    }

    private void assertPreflightFailure(InvalidStateBuilder invalidStateBuilder) {
        String schema = schemaName("preflight");
        try {
            migrateTo(schema, "29");
            UUID applicationId = insertValidApplication(schema);
            invalidStateBuilder.build(schema, applicationId);

            assertThrows(FlywayException.class, () -> migrateLatest(schema));
            assertEquals("29", latestVersion(schema));
        } finally {
            dropSchema(schema);
        }
    }

    private UUID insertValidApplication(String schema) {
        Map<String, Object> salaryAdvance = product(schema, "SALARY_ADVANCE");
        UUID applicationId = UUID.randomUUID();
        insertApplication(
                schema,
                applicationId,
                (UUID) salaryAdvance.get("id"),
                (String) salaryAdvance.get("product_code"),
                (String) salaryAdvance.get("product_type")
        );
        return applicationId;
    }

    private void insertApplication(
            String schema,
            UUID applicationId,
            UUID loanProductId,
            String productCode,
            String productType
    ) {
        UUID customerId = jdbc.queryForObject(
                "select id from " + schema + ".customers order by id limit 1",
                UUID.class
        );
        jdbc.update(
                "insert into " + schema + ".loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,"
                        + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,?,?,'EXPIRED',1000000,1,?)",
                applicationId,
                customerId,
                loanProductId,
                "V30-" + applicationId.toString().substring(0, 12).toUpperCase(),
                productCode,
                productType,
                LocalDateTime.of(2026, 7, 28, 10, 0)
        );
    }

    private Map<String, Object> product(String schema, String productCode) {
        return jdbc.queryForMap(
                "select id,product_code,product_type from " + schema
                        + ".loan_products where product_code = ?",
                productCode
        );
    }

    private boolean applicationExists(String schema, UUID applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from " + schema
                        + ".loan_applications where id = ?)",
                Boolean.class,
                applicationId
        ));
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
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

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schemaName(String suffix) {
        return "loan_product_v30_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    @FunctionalInterface
    private interface InvalidStateBuilder {
        void build(String schema, UUID applicationId);
    }
}

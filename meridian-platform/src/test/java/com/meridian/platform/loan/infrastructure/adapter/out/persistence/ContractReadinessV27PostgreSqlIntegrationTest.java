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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class ContractReadinessV27PostgreSqlIntegrationTest {
    private static final String SCHEMA = schemaName("installed");

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test void installsV27AndRejectsASecondReservedMovementForOneApplication() {
        assertEquals(1, jdbc.queryForObject("select count(*) from " + SCHEMA
                + ".flyway_schema_history where version = '27' and success", Integer.class));
        UUID applicationId = insertReservationFixture(SCHEMA, false);

        DataAccessException duplicate = assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into " + SCHEMA + ".salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                        + "select ?,salary_advance_limit_id,loan_application_id,'RESERVED',amount,current_timestamp "
                        + "from " + SCHEMA + ".salary_advance_limit_movements "
                        + "where loan_application_id = ? and movement_type = 'RESERVED'",
                UUID.randomUUID(), applicationId));

        assertTrue(allMessages(duplicate).contains(
                "uq_salary_advance_limit_movements_application_reserved"));
    }

    @Test void v27FailsClearlyWhenExistingApplicationsHaveDuplicateReservedMovements() {
        String schema = schemaName("duplicate");
        try {
            migrateTo(schema, "26");
            insertReservationFixture(schema, true);

            FlywayException failure = assertThrows(FlywayException.class, () -> migrateLatest(schema));

            assertTrue(allMessages(failure).contains(
                    "V27 cannot enforce one RESERVED movement per Loan Application"));
            assertEquals(0, jdbc.queryForObject("select count(*) from pg_indexes "
                    + "where schemaname = ? and indexname = "
                    + "'uq_salary_advance_limit_movements_application_reserved'", Integer.class, schema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private UUID insertReservationFixture(String schema, boolean duplicate) {
        UUID customerId = jdbc.queryForObject(
                "select id from " + schema + ".customers order by customer_number limit 1", UUID.class);
        UUID productId = jdbc.queryForObject(
                "select id from " + schema + ".loan_products where product_code = 'SALARY_ADVANCE'", UUID.class);
        UUID applicationId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,product_type,status,"
                        + "requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','EXPIRED',1000,1,current_timestamp)",
                applicationId, customerId, productId, "SA-V27-" + applicationId);
        jdbc.update("insert into " + schema + ".salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,total_limit,used_amount,reserved_amount,"
                        + "available_amount,status,last_refreshed_at) values (?,?,?,2000,0,1000,1000,'ACTIVE',current_timestamp)",
                limitId, customerId, UUID.randomUUID());
        jdbc.update("insert into " + schema + ".salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                        + "values (?,?,?,'RESERVED',1000,current_timestamp)",
                UUID.randomUUID(), limitId, applicationId);
        if (duplicate) {
            jdbc.update("insert into " + schema + ".salary_advance_limit_movements "
                            + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                            + "values (?,?,?,'RESERVED',1000,current_timestamp)",
                    UUID.randomUUID(), limitId, applicationId);
        }
        return applicationId;
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private void migrateLatest(String schema) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
    }

    private static String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }

    private static String schemaName(String suffix) {
        return "mc_v27_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.dto.ChangeLoanProductActivationRequest;
import com.meridian.platform.loan.application.dto.UpdateLoanProductLimitsRequest;
import com.meridian.platform.loan.application.port.in.ManageLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.QueryAdminLoanProductsUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class LoanProductAdministrationPostgreSqlIntegrationTest {
    private static final String SCHEMA = "loan_product_admin_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");

    @Autowired ManageLoanProductUseCase commands;
    @Autowired QueryAdminLoanProductsUseCase queries;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void resetProduct() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACTOR_ID, "backoffice.admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("loan:product:manage")
        ));
        jdbcTemplate.update(
                "UPDATE loan_products SET active = TRUE, min_amount = 1000000.00, "
                        + "max_amount = 50000000.00, updated_at = TIMESTAMP '2020-01-01 00:00:00' "
                        + "WHERE product_code = 'UNSECURED_CONSUMER_LOAN'"
        );
    }

    @Test
    void listsAllProductsInDeterministicCodeOrderIncludingInactiveProducts() {
        jdbcTemplate.update("UPDATE loan_products SET active = FALSE WHERE product_code = 'COLLATERAL_LOAN'");

        var products = queries.findAll();

        assertEquals(List.of("COLLATERAL_LOAN", "SALARY_ADVANCE", "UNSECURED_CONSUMER_LOAN"),
                products.stream().map(product -> product.productCode()).toList());
        assertFalse(products.getFirst().active());
    }

    @Test
    void realLimitMutationPreservesCreatedAtUpdatesTimestampAndAuditsOnce() {
        LocalDateTime createdAt = timestamp("created_at");
        LocalDateTime originalUpdatedAt = timestamp("updated_at");
        int applicationsBefore = count("loan_applications");
        int accountsBefore = count("loan_accounts");

        commands.updateLimits("UNSECURED_CONSUMER_LOAN", new UpdateLoanProductLimitsRequest(
                new BigDecimal("750000.00"), new BigDecimal("60000000.00")
        ));
        LocalDateTime changedUpdatedAt = timestamp("updated_at");
        commands.updateLimits("UNSECURED_CONSUMER_LOAN", new UpdateLoanProductLimitsRequest(
                new BigDecimal("750000.0"), new BigDecimal("60000000")
        ));

        assertEquals(createdAt, timestamp("created_at"));
        assertNotEquals(originalUpdatedAt, changedUpdatedAt);
        assertEquals(changedUpdatedAt, timestamp("updated_at"));
        assertEquals(1, auditCount("LOAN_PRODUCT_LIMITS_UPDATED"));
        assertEquals(ACTOR_ID, jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_events WHERE action = 'LOAN_PRODUCT_LIMITS_UPDATED'",
                UUID.class
        ));
        assertEquals("UNSECURED_CONSUMER_LOAN", jdbcTemplate.queryForObject(
                "SELECT payload ->> 'productCode' FROM audit_events "
                        + "WHERE action = 'LOAN_PRODUCT_LIMITS_UPDATED'",
                String.class
        ));
        assertEquals(applicationsBefore, count("loan_applications"));
        assertEquals(accountsBefore, count("loan_accounts"));
    }

    @Test
    void concurrentSameTargetActivationSerializesToOneMutationAndAudit() throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return commands.changeActivation(
                        "UNSECURED_CONSUMER_LOAN", new ChangeLoanProductActivationRequest(false)
                ).active();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return commands.changeActivation(
                        "UNSECURED_CONSUMER_LOAN", new ChangeLoanProductActivationRequest(false)
                ).active();
            });
            start.await(5, TimeUnit.SECONDS);

            assertFalse(first.get(10, TimeUnit.SECONDS));
            assertFalse(second.get(10, TimeUnit.SECONDS));
        }

        assertFalse(jdbcTemplate.queryForObject(
                "SELECT active FROM loan_products WHERE product_code = 'UNSECURED_CONSUMER_LOAN'",
                Boolean.class
        ));
        assertEquals(1, auditCount("LOAN_PRODUCT_DEACTIVATED"));
    }

    private int auditCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE entity_type = 'LOAN_PRODUCT' AND action = ?",
                Integer.class,
                action
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private LocalDateTime timestamp(String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM loan_products WHERE product_code = 'UNSECURED_CONSUMER_LOAN'",
                LocalDateTime.class
        );
    }
}

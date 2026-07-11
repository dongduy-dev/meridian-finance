package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class RespondToApprovedOfferRollbackPostgresIntegrationTest {

    private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID LINK_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Autowired
    private RespondToApprovedOfferUseCase respondToApprovedOfferUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackOfferDeclineHistoryAuditAndReservationReleaseWhenSecondAuditWriteFails() {
        UUID loanApplicationId = insertPendingOfferFixture();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> respondToApprovedOfferUseCase.declineOffer(loanApplicationId)
        );

        assertEquals("forced second audit failure", exception.getMessage());
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", stringValue(
                "SELECT status FROM loan_applications WHERE id = ?",
                loanApplicationId
        ));
        assertEquals("PENDING", stringValue(
                "SELECT status FROM approved_offers WHERE loan_application_id = ?",
                loanApplicationId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT declined_at FROM approved_offers WHERE loan_application_id = ?",
                Timestamp.class,
                loanApplicationId
        ));
        assertEquals(0, intValue(
                "SELECT COUNT(*) FROM loan_application_status_transitions WHERE loan_application_id = ?",
                loanApplicationId
        ));
        assertEquals(0, intValue(
                "SELECT COUNT(*) FROM audit_events WHERE entity_id = ? AND action = 'OFFER_DECLINED'",
                offerIdFor(loanApplicationId)
        ));
        assertEquals(0, intValue(
                "SELECT COUNT(*) FROM salary_advance_limit_movements WHERE loan_application_id = ? AND movement_type = ?",
                loanApplicationId,
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED.name()
        ));
        assertMoneyEquals("3000000.00", moneyValue(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ? AND customer_partner_employee_link_id = ?",
                CUSTOMER_ID,
                LINK_ID
        ));
        assertMoneyEquals("3000000.00", moneyValue(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ? AND customer_partner_employee_link_id = ?",
                CUSTOMER_ID,
                LINK_ID
        ));
    }

    private UUID insertPendingOfferFixture() {
        cleanupPreviousFixtureRows();

        UUID loanApplicationId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID policyId = jdbcTemplate.queryForObject(
                """
                        SELECT policy.id
                        FROM loan_product_policies policy
                        JOIN loan_products product ON product.id = policy.loan_product_id
                        WHERE product.product_code = 'SALARY_ADVANCE'
                          AND policy.policy_code = 'DEFAULT_POLICY'
                        """,
                UUID.class
        );

        jdbcTemplate.update(
                """
                        INSERT INTO loan_applications (
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
                        )
                        SELECT ?, ?, id, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'CUSTOMER_ACCEPTANCE_PENDING',
                               3000000.00, 1, ?
                        FROM loan_products
                        WHERE product_code = 'SALARY_ADVANCE'
                        """,
                loanApplicationId,
                CUSTOMER_ID,
                "SA-RB-" + loanApplicationId.toString().substring(0, 8),
                Timestamp.valueOf(NOW.minusDays(1))
        );
        jdbcTemplate.update(
                """
                        INSERT INTO salary_advance_limits (
                            id,
                            customer_id,
                            customer_partner_employee_link_id,
                            total_limit,
                            used_amount,
                            reserved_amount,
                            available_amount,
                            status,
                            last_refreshed_at
                        )
                        VALUES (?, ?, ?, 6000000.00, 0.00, 3000000.00, 3000000.00, 'ACTIVE', ?)
                        """,
                limitId,
                CUSTOMER_ID,
                LINK_ID,
                Timestamp.valueOf(NOW.minusDays(1))
        );
        jdbcTemplate.update(
                """
                        INSERT INTO salary_advance_verifications (
                            id,
                            loan_application_id,
                            customer_id,
                            customer_partner_employee_link_id,
                            salary_advance_limit_id,
                            partner_company_id,
                            partner_employee_id,
                            source_import_batch_id,
                            employee_verification_outcome,
                            product_verification_result,
                            total_limit_snapshot,
                            used_amount_snapshot,
                            reserved_amount_snapshot,
                            available_limit_snapshot,
                            verified_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED',
                                6000000.00, 0.00, 3000000.00, 3000000.00, ?)
                        """,
                verificationId,
                loanApplicationId,
                CUSTOMER_ID,
                LINK_ID,
                limitId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.valueOf(NOW.minusDays(1))
        );
        jdbcTemplate.update(
                """
                        INSERT INTO approved_offers (
                            id,
                            loan_application_id,
                            source_loan_product_policy_id,
                            status,
                            approved_principal,
                            approved_term_months,
                            interest_calculation_method,
                            flat_monthly_interest_rate,
                            total_interest,
                            fee_amount,
                            total_repayment_amount,
                            repayment_method,
                            generated_at,
                            expires_at
                        )
                        VALUES (?, ?, ?, 'PENDING', 3000000.00, 1, 'FLAT_ORIGINAL_PRINCIPAL',
                                0.012000, 36000.00, 0.00, 3036000.00, 'ON_SALARY_DATE', ?, ?)
                        """,
                offerId,
                loanApplicationId,
                policyId,
                Timestamp.valueOf(NOW.minusDays(1)),
                Timestamp.valueOf(NOW.plusDays(1))
        );
        jdbcTemplate.update(
                """
                        INSERT INTO approved_offer_repayment_items (
                            id,
                            approved_offer_id,
                            installment_number,
                            principal_due,
                            interest_due,
                            fee_due,
                            total_due
                        )
                        VALUES (?, ?, 1, 3000000.00, 36000.00, 0.00, 3036000.00)
                        """,
                UUID.randomUUID(),
                offerId
        );
        return loanApplicationId;
    }

    private void cleanupPreviousFixtureRows() {
        jdbcTemplate.update(
                """
                        DELETE FROM approved_offer_repayment_items
                        WHERE approved_offer_id IN (
                            SELECT offer.id
                            FROM approved_offers offer
                            JOIN loan_applications application ON application.id = offer.loan_application_id
                            WHERE application.customer_id = ?
                              AND application.product_code = 'SALARY_ADVANCE'
                        )
                        """,
                CUSTOMER_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM approved_offers
                        WHERE loan_application_id IN (
                            SELECT id
                            FROM loan_applications
                            WHERE customer_id = ?
                              AND product_code = 'SALARY_ADVANCE'
                        )
                        """,
                CUSTOMER_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM salary_advance_verifications
                        WHERE loan_application_id IN (
                            SELECT id
                            FROM loan_applications
                            WHERE customer_id = ?
                              AND product_code = 'SALARY_ADVANCE'
                        )
                        """,
                CUSTOMER_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM salary_advance_limit_movements
                        WHERE salary_advance_limit_id IN (
                            SELECT id
                            FROM salary_advance_limits
                            WHERE customer_id = ?
                              AND customer_partner_employee_link_id = ?
                        )
                           OR loan_application_id IN (
                            SELECT id
                            FROM loan_applications
                            WHERE customer_id = ?
                              AND product_code = 'SALARY_ADVANCE'
                        )
                        """,
                CUSTOMER_ID,
                LINK_ID,
                CUSTOMER_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM loan_applications
                        WHERE customer_id = ?
                          AND product_code = 'SALARY_ADVANCE'
                        """,
                CUSTOMER_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM salary_advance_limits
                        WHERE customer_id = ?
                          AND customer_partner_employee_link_id = ?
                        """,
                CUSTOMER_ID,
                LINK_ID
        );
    }
    private UUID offerIdFor(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM approved_offers WHERE loan_application_id = ?",
                UUID.class,
                loanApplicationId
        );
    }

    private String stringValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private int intValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal moneyValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    private void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }

    @TestConfiguration
    static class RollbackTestConfig {

        @Bean
        @Primary
        CurrentUserProvider rollbackTestCurrentUserProvider() {
            return () -> new AuthenticatedUser(
                    CUSTOMER_USER_ID,
                    "customer.demo@meridian.local",
                    "CUSTOMER",
                    CUSTOMER_ID,
                    Set.of("CUSTOMER"),
                    Set.of("loan:offer:respond:own")
            );
        }

        @Bean
        @Primary
        AuditEventPublisher failingOnSecondAuditEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            return new FailingOnSecondAuditEventPublisher(applicationEventPublisher);
        }
    }

    private static class FailingOnSecondAuditEventPublisher implements AuditEventPublisher {

        private final ApplicationEventPublisher applicationEventPublisher;
        private int publishCount;

        private FailingOnSecondAuditEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            this.applicationEventPublisher = applicationEventPublisher;
        }

        @Override
        public void publish(AuditRecordRequestedEvent event) {
            if (!Thread.currentThread().getName().equals("main")) {
                applicationEventPublisher.publishEvent(event);
                return;
            }
            publishCount++;
            if (publishCount == 2) {
                throw new IllegalStateException("forced second audit failure");
            }
            applicationEventPublisher.publishEvent(event);
        }
    }
}





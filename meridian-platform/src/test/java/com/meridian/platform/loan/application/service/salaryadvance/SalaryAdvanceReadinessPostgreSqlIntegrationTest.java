package com.meridian.platform.loan.application.service.salaryadvance;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.QuerySalaryAdvanceReadinessUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                SalaryAdvanceReadinessPostgreSqlIntegrationTest.UserConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.loan.overdue-evaluation.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class SalaryAdvanceReadinessPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "salary_advance_readiness_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final BigDecimal REQUESTED_AMOUNT = money("3000000.00");

    @Autowired
    private QuerySalaryAdvanceReadinessUseCase readinessQueries;

    @Autowired
    private QueryLoanApplicationUseCase applicationQueries;

    @Autowired
    private StartSalaryAdvanceApplicationUseCase submissions;

    @Autowired
    private StartLoanApplicationReviewUseCase reviews;

    @Autowired
    private CustomerSensitiveValueProtector sensitiveValueProtector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableCurrentUserProvider currentUser;

    private Fixture fixture;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        currentUser.customer(fixture.customerUserId(), fixture.customerId());
    }

    @AfterEach
    void clearCurrentUser() {
        currentUser.clear();
    }

    @Test
    void readinessReturnsAuthoritativeValuesWithoutCreatingWorkflowState() {
        int applicationCount = count("select count(*) from loan_applications");
        int limitCount = count("select count(*) from salary_advance_limits");
        int movementCount = count("select count(*) from salary_advance_limit_movements");

        SalaryAdvanceReadinessDto result = readinessQueries.queryReadiness();

        assertTrue(result.applicationAllowed());
        assertEquals("ELIGIBLE", result.partnerEligibilityStatus());
        assertEquals("NOT_INITIALIZED", result.limitStatus());
        assertEquals(fixture.linkId(), result.customerPartnerEmployeeLinkId());
        assertEquals(money("6000000.00"), result.totalAmount());
        assertEquals(money("6000000.00"), result.availableAmount());
        assertTrue(result.blockerCodes().isEmpty());
        assertEquals(applicationCount, count("select count(*) from loan_applications"));
        assertEquals(limitCount, count("select count(*) from salary_advance_limits"));
        assertEquals(movementCount, count("select count(*) from salary_advance_limit_movements"));
    }

    @Test
    void newerCurrentMonthBatchFailsClosedAndRefreshRestoresSubmission() {
        UUID replacementBatchId = UUID.randomUUID();
        UUID replacementEmployeeId = UUID.randomUUID();
        jdbc.update(
                "insert into partner_employee_import_batches "
                        + "(id, partner_company_id, effective_month, status, valid_row_count, "
                        + "invalid_row_count, created_at, updated_at) "
                        + "values (?, ?, '2026-06', 'COMPLETED', 1, 0, "
                        + "timestamp '2026-06-15 11:00:00', timestamp '2026-06-15 11:00:00')",
                replacementBatchId,
                fixture.partnerCompanyId()
        );
        jdbc.update(
                "insert into partner_employees "
                        + "(id, partner_company_id, import_batch_id, employee_code, identity_reference, "
                        + "salary_amount, salary_advance_limit, employment_status, active) "
                        + "values (?, ?, ?, 'READINESS-EMP', 'READINESS-ID', 20000000.00, "
                        + "6000000.00, 'ACTIVE', true)",
                replacementEmployeeId,
                fixture.partnerCompanyId(),
                replacementBatchId
        );

        SalaryAdvanceReadinessDto stale = readinessQueries.queryReadiness();
        assertFalse(stale.applicationAllowed());
        assertEquals("EVIDENCE_STALE", stale.partnerEligibilityStatus());
        assertTrue(stale.blockerCodes().contains("SALARY_ADVANCE_ELIGIBILITY_DATA_STALE"));

        int applicationCount = count("select count(*) from loan_applications where customer_id=?",
                fixture.customerId());
        int limitCount = count("select count(*) from salary_advance_limits where customer_id=?",
                fixture.customerId());
        int movementCount = count(
                "select count(*) from salary_advance_limit_movements movement "
                        + "join salary_advance_limits sal on sal.id=movement.salary_advance_limit_id "
                        + "where sal.customer_id=?",
                fixture.customerId()
        );

        BusinessRuleViolationException failure = assertThrows(
                BusinessRuleViolationException.class,
                () -> submissions.startSalaryAdvanceApplication(
                        new SalaryAdvanceApplicationRequest(
                                fixture.linkId(),
                                REQUESTED_AMOUNT,
                                1
                        )
                )
        );
        assertEquals("SALARY_ADVANCE_ELIGIBILITY_DATA_STALE", failure.getErrorCode());
        assertEquals(applicationCount, count(
                "select count(*) from loan_applications where customer_id=?", fixture.customerId()));
        assertEquals(limitCount, count(
                "select count(*) from salary_advance_limits where customer_id=?", fixture.customerId()));
        assertEquals(movementCount, count(
                "select count(*) from salary_advance_limit_movements movement "
                        + "join salary_advance_limits sal on sal.id=movement.salary_advance_limit_id "
                        + "where sal.customer_id=?",
                fixture.customerId()
        ));
        assertEquals(0, count(
                "select count(*) from salary_advance_verifications verification "
                        + "join loan_applications application on application.id=verification.loan_application_id "
                        + "where application.customer_id=?",
                fixture.customerId()
        ));

        jdbc.update(
                "update customer_partner_employee_links set partner_employee_id=?, "
                        + "source_import_batch_id=?, last_verified_at=timestamp '2026-06-15 11:05:00', "
                        + "last_refreshed_at=timestamp '2026-06-15 11:05:00' where id=?",
                replacementEmployeeId,
                replacementBatchId,
                fixture.linkId()
        );

        SalaryAdvanceReadinessDto refreshed = readinessQueries.queryReadiness();
        assertTrue(refreshed.applicationAllowed());
        assertEquals("ELIGIBLE", refreshed.partnerEligibilityStatus());
        SalaryAdvanceApplicationDto application = submissions.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), REQUESTED_AMOUNT, 1)
        );
        assertEquals("SUBMITTED", application.status());
        assertEquals(1, count(
                "select count(*) from salary_advance_verifications where loan_application_id=?",
                application.loanApplicationId()
        ));
    }

    @Test
    void durableStatusCanBeReadAgainAfterASeparateWorkflowTransition() {
        SalaryAdvanceApplicationDto application = submissions.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), REQUESTED_AMOUNT, 1)
        );

        LoanApplicationStatusDto submitted = applicationQueries.query(application.loanApplicationId());
        assertEquals("SUBMITTED", submitted.status());

        currentUser.loanOfficer();
        reviews.startReview(application.loanApplicationId());
        LoanApplicationStatusDto staffView = applicationQueries.query(application.loanApplicationId());
        assertEquals("UNDER_REVIEW", staffView.status());

        currentUser.customer(fixture.customerUserId(), fixture.customerId());
        LoanApplicationStatusDto resumedView = applicationQueries.query(application.loanApplicationId());
        assertEquals("UNDER_REVIEW", resumedView.status());

        Fixture foreign = createFixture();
        currentUser.customer(fixture.customerUserId(), fixture.customerId());
        SalaryAdvanceApplicationDto foreignApplication;
        currentUser.customer(foreign.customerUserId(), foreign.customerId());
        foreignApplication = submissions.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(foreign.linkId(), REQUESTED_AMOUNT, 1)
        );
        currentUser.customer(fixture.customerUserId(), fixture.customerId());
        EntityNotFoundException concealed = assertThrows(
                EntityNotFoundException.class,
                () -> applicationQueries.query(foreignApplication.loanApplicationId())
        );
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class,
                () -> applicationQueries.query(UUID.randomUUID())
        );
        assertEquals("LOAN_APPLICATION_NOT_FOUND", concealed.getErrorCode());
        assertEquals(missing.getErrorCode(), concealed.getErrorCode());
    }

    private Fixture createFixture() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID partnerCompanyId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        UUID partnerEmployeeId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");

        jdbc.update(
                "insert into partner_companies "
                        + "(id, company_code, name, status, salary_advance_policy_limit) "
                        + "values (?, ?, 'Readiness Employer', 'ACTIVE', 7000000.00)",
                partnerCompanyId,
                "RDY-" + unique.substring(0, 12)
        );
        jdbc.update(
                "insert into partner_employee_import_batches "
                        + "(id, partner_company_id, effective_month, status, valid_row_count, "
                        + "invalid_row_count, created_at, updated_at) "
                        + "values (?, ?, '2026-06', 'COMPLETED', 1, 0, "
                        + "timestamp '2026-06-15 10:00:00', timestamp '2026-06-15 10:00:00')",
                importBatchId,
                partnerCompanyId
        );
        jdbc.update(
                "insert into partner_employees "
                        + "(id, partner_company_id, import_batch_id, employee_code, identity_reference, "
                        + "salary_amount, salary_advance_limit, employment_status, active) "
                        + "values (?, ?, ?, 'READINESS-EMP', 'READINESS-ID', 20000000.00, "
                        + "6000000.00, 'ACTIVE', true)",
                partnerEmployeeId,
                partnerCompanyId,
                importBatchId
        );
        jdbc.update(
                "insert into customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "values (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId,
                "CUS-R-" + unique.substring(0, 12)
        );
        jdbc.update(
                "insert into customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, terms_consent_accepted, "
                        + "data_processing_consent_accepted) "
                        + "values (?, ?, 'Readiness Customer', ?, ?, '0001', '0900000000', "
                        + "'Test Address', 'EMPLOYED', 'Readiness Employer', true, true)",
                UUID.randomUUID(),
                customerId,
                "cipher-" + unique,
                "fingerprint-" + unique
        );
        ProtectedSensitiveValue bankAccount =
                sensitiveValueProtector.protectBankAccountNumber("TEST", "0000123456785678");
        jdbc.update(
                "insert into customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, "
                        + "account_number_last_four, status, primary_account) "
                        + "values (?, ?, 'TEST', 'Test Bank', 'Readiness Customer', ?, ?, "
                        + "'5678', 'ACTIVE', true)",
                UUID.randomUUID(),
                customerId,
                bankAccount.ciphertext(),
                bankAccount.fingerprint()
        );
        jdbc.update(
                "insert into users "
                        + "(id, email, normalized_email, password_hash, user_type, status, "
                        + "display_name, customer_id) values (?, ?, ?, 'not-used', 'CUSTOMER', "
                        + "'ACTIVE', 'Readiness Customer', ?)",
                customerUserId,
                "readiness-" + unique + "@meridian.test",
                "readiness-" + unique + "@meridian.test",
                customerId
        );
        jdbc.update(
                "insert into customer_partner_employee_links "
                        + "(id, customer_id, partner_company_id, partner_employee_id, "
                        + "source_import_batch_id, verification_outcome, link_status, "
                        + "verified_identity_ref, verified_employee_code, last_verified_at, "
                        + "last_refreshed_at) values (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', "
                        + "'READINESS-ID', 'READINESS-EMP', timestamp '2026-06-15 10:00:00', "
                        + "timestamp '2026-06-15 10:00:00')",
                linkId,
                customerId,
                partnerCompanyId,
                partnerEmployeeId,
                importBatchId
        );
        return new Fixture(
                customerId,
                customerUserId,
                partnerCompanyId,
                importBatchId,
                partnerEmployeeId,
                linkId
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private record Fixture(
            UUID customerId,
            UUID customerUserId,
            UUID partnerCompanyId,
            UUID importBatchId,
            UUID partnerEmployeeId,
            UUID linkId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UserConfiguration {

        @Bean
        @Primary
        MutableCurrentUserProvider mutableCurrentUserProvider() {
            return new MutableCurrentUserProvider();
        }

        @Bean
        @Primary
        Clock readinessClock() {
            return Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    static class MutableCurrentUserProvider implements CurrentUserProvider {

        private final ThreadLocal<AuthenticatedUser> users = new ThreadLocal<>();

        void customer(UUID userId, UUID customerId) {
            users.set(new AuthenticatedUser(
                    userId,
                    "readiness-customer@meridian.test",
                    "CUSTOMER",
                    customerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:submit", "loan:read:own")
            ));
        }

        void loanOfficer() {
            users.set(new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000302"),
                    "readiness-loan-officer@meridian.test",
                    "STAFF",
                    null,
                    Set.of("LOAN_OFFICER"),
                    Set.of("loan:review", "loan:read")
            ));
        }

        void clear() {
            users.remove();
        }

        @Override
        public AuthenticatedUser currentUser() {
            AuthenticatedUser user = users.get();
            if (user == null) {
                throw new IllegalStateException("No test user is active.");
            }
            return user;
        }
    }
}

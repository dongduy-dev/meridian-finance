package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                SalaryAdvanceSubmissionConcurrencyPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class
        },
        properties = "meridian.loan.offer-expiry.enabled=false"
)
class SalaryAdvanceSubmissionConcurrencyPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_submission_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID FIRST_PARTNER_COMPANY_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_PARTNER_EMPLOYEE_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private static final UUID FIRST_IMPORT_BATCH_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID SECOND_PARTNER_COMPANY_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_PARTNER_EMPLOYEE_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb04");
    private static final UUID SECOND_IMPORT_BATCH_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final BigDecimal REQUESTED_AMOUNT = money(3_000_000);

    @Autowired
    private StartSalaryAdvanceApplicationUseCase submissionUseCase;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableCurrentUserProvider currentUserProvider;

    private Fixture fixture;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        currentUserProvider.use(fixture.userId(), fixture.customerId());
    }

    @Test
    void customerProductAdvisoryLockIsHeldUntilTransactionCompletion() throws Exception {
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondAcquired = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                loanApplicationRepository.acquireCustomerProductLock(
                        fixture.customerId(),
                        ProductCode.SALARY_ADVANCE
                );
                firstAcquired.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstAcquired.await(5, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                secondStarted.countDown();
                loanApplicationRepository.acquireCustomerProductLock(
                        fixture.customerId(),
                        ProductCode.SALARY_ADVANCE
                );
                secondAcquired.countDown();
            }));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            try {
                assertFalse(secondAcquired.await(300, TimeUnit.MILLISECONDS));
            } finally {
                releaseFirst.countDown();
            }

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(0, secondAcquired.getCount());
        }
    }


    @Test
    void concurrentSameLinkSubmissionsLeaveOneCompleteWinner() throws Exception {
        List<SubmissionOutcome> outcomes = submitConcurrently(fixture.firstLinkId(), fixture.firstLinkId());

        assertOneWinnerAndStableConflict(outcomes);
        assertCompleteWinnerState(fixture.firstLinkId(), null);
    }

    @Test
    void concurrentDifferentLinkSubmissionsLeaveNoLoserFinancialResidue() throws Exception {
        List<SubmissionOutcome> outcomes = submitConcurrently(fixture.firstLinkId(), fixture.secondLinkId());

        assertOneWinnerAndStableConflict(outcomes);
        UUID winnerLinkId = jdbcTemplate.queryForObject(
                "SELECT customer_partner_employee_link_id FROM salary_advance_verifications WHERE customer_id = ?",
                UUID.class,
                fixture.customerId()
        );
        UUID loserLinkId = winnerLinkId.equals(fixture.firstLinkId())
                ? fixture.secondLinkId()
                : fixture.firstLinkId();
        assertCompleteWinnerState(winnerLinkId, loserLinkId);
    }

    @Test
    void exactActiveApplicationConstraintTranslatesWithoutNormalLockPath() {
        UUID productId = salaryAdvanceProductId();
        transactionTemplate.executeWithoutResult(status -> loanApplicationRepository.save(
                application(productId, "SA-FALLBACK-ONE")
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> transactionTemplate.executeWithoutResult(status -> loanApplicationRepository.save(
                        application(productId, "SA-FALLBACK-TWO")
                ))
        );

        assertEquals("BLOCKING_APPLICATION_EXISTS", exception.getErrorCode());
        assertEquals(
                "A blocking Salary Advance application already exists for this customer.",
                exception.getMessage()
        );
        assertEquals(1, count("SELECT count(*) FROM loan_applications WHERE customer_id = ?", fixture.customerId()));
    }

    @Test
    void unrelatedIntegrityViolationIsNotMisclassified() {
        LoanApplication invalidProductApplication = application(
                UUID.randomUUID(),
                "SA-INVALID-FK"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> transactionTemplate.executeWithoutResult(status -> loanApplicationRepository.save(
                        invalidProductApplication
                ))
        );
    }

    @Test
    void uniqueConflictRollsBackPriorLimitMutationInSameTransaction() {
        UUID productId = salaryAdvanceProductId();
        insertLimit(fixture.firstLinkId(), money(6_000_000));
        transactionTemplate.executeWithoutResult(status -> loanApplicationRepository.save(
                application(productId, "SA-ROLLBACK-ONE")
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    SalaryAdvanceLimit currentLimit = salaryAdvanceLimitRepository
                            .findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                                    fixture.customerId(),
                                    fixture.firstLinkId()
                            )
                            .orElseThrow();
                    salaryAdvanceLimitRepository.save(currentLimit.reserve(money(1_000_000)));
                    loanApplicationRepository.save(application(productId, "SA-ROLLBACK-TWO"));
                })
        );

        assertEquals("BLOCKING_APPLICATION_EXISTS", exception.getErrorCode());
        assertEquals(money(0), amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ? AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                fixture.firstLinkId()
        ));
        assertEquals(money(6_000_000), amount(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ? AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                fixture.firstLinkId()
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Latch timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting latch.", exception);
        }
    }

    private List<SubmissionOutcome> submitConcurrently(UUID firstLinkId, UUID secondLinkId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SubmissionOutcome> first = executor.submit(() -> submit(firstLinkId, ready, start));
            Future<SubmissionOutcome> second = executor.submit(() -> submit(secondLinkId, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Concurrent workers did not reach the start barrier.");
            start.countDown();
            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        }
    }

    private SubmissionOutcome submit(UUID linkId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return SubmissionOutcome.failure(new AssertionError("Concurrent start barrier timed out."));
            }
            return SubmissionOutcome.success(submissionUseCase.startSalaryAdvanceApplication(
                    new SalaryAdvanceApplicationRequest(linkId, REQUESTED_AMOUNT, 1)
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SubmissionOutcome.failure(exception);
        } catch (Throwable throwable) {
            return SubmissionOutcome.failure(throwable);
        }
    }

    private void assertOneWinnerAndStableConflict(List<SubmissionOutcome> outcomes) {
        List<SubmissionOutcome> successes = outcomes.stream().filter(SubmissionOutcome::successful).toList();
        List<SubmissionOutcome> failures = outcomes.stream().filter(outcome -> !outcome.successful()).toList();

        assertEquals(1, successes.size());
        assertEquals(1, failures.size());
        BusinessStateConflictException conflict = assertInstanceOf(
                BusinessStateConflictException.class,
                failures.getFirst().failure()
        );
        assertEquals("BLOCKING_APPLICATION_EXISTS", conflict.getErrorCode());
        assertEquals(1, count("SELECT count(*) FROM loan_applications WHERE customer_id = ?", fixture.customerId()));
    }

    private void assertCompleteWinnerState(UUID winnerLinkId, UUID loserLinkId) {
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_limit_movements WHERE movement_type = 'RESERVED' "
                        + "AND loan_application_id IN (SELECT id FROM loan_applications WHERE customer_id = ?)",
                fixture.customerId()
        ));
        assertEquals(2, count(
                "SELECT count(*) FROM salary_advance_limit_movements WHERE salary_advance_limit_id IN "
                        + "(SELECT id FROM salary_advance_limits WHERE customer_id = ?)",
                fixture.customerId()
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_limit_movements WHERE movement_type = 'INITIALIZED' "
                        + "AND salary_advance_limit_id IN (SELECT id FROM salary_advance_limits WHERE customer_id = ?)",
                fixture.customerId()
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_verifications WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM loan_application_status_transitions WHERE loan_application_id IN "
                        + "(SELECT id FROM loan_applications WHERE customer_id = ?)",
                fixture.customerId()
        ));
        assertEquals(4, count(
                "SELECT count(*) FROM audit_events WHERE actor_user_id = ?",
                fixture.userId()
        ));
        assertEquals(
                List.of(
                        "DOCUMENT_CHECKLIST_CREATED",
                        "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                        "SALARY_ADVANCE_LIMIT_INITIALIZED",
                        "SALARY_ADVANCE_LIMIT_RESERVED"
                ),
                jdbcTemplate.queryForList(
                        "SELECT action FROM audit_events WHERE actor_user_id = ? ORDER BY sequence_number",
                        String.class,
                        fixture.userId()
                )
        );
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));

        BigDecimal totalLimit = winnerLinkId.equals(fixture.firstLinkId())
                ? money(6_000_000)
                : money(9_000_000);
        assertEquals(totalLimit, amount(
                "SELECT total_limit FROM salary_advance_limits WHERE customer_id = ? "
                        + "AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                winnerLinkId
        ));
        assertEquals(REQUESTED_AMOUNT, amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ? "
                        + "AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                winnerLinkId
        ));
        assertEquals(totalLimit.subtract(REQUESTED_AMOUNT), amount(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ? "
                        + "AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                winnerLinkId
        ));
        if (loserLinkId != null) {
            assertEquals(0, count(
                    "SELECT count(*) FROM salary_advance_limits WHERE customer_id = ? "
                            + "AND customer_partner_employee_link_id = ?",
                    fixture.customerId(),
                    loserLinkId
            ));
        }
    }

    private Fixture createFixture() {
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID firstLinkId = UUID.randomUUID();
        UUID secondLinkId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");

        jdbcTemplate.update(
                "INSERT INTO customers (id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId,
                "CUS-T-" + unique.substring(0, 12)
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_profiles (
                            id, customer_id, full_name, identity_reference_ciphertext,
                            identity_reference_fingerprint, identity_reference_last_four,
                            phone_number, residential_address, employment_status, employer_name,
                            terms_consent_accepted, data_processing_consent_accepted
                        ) VALUES (?, ?, 'Concurrency Test Customer', ?, ?, '1234', '0900000000',
                                  'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)
                        """,
                UUID.randomUUID(),
                customerId,
                "cipher-" + unique,
                "fingerprint-" + unique
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_bank_accounts (
                            id, customer_id, bank_code, bank_name_snapshot, account_holder_name,
                            account_number_ciphertext, account_number_fingerprint, account_number_last_four,
                            status, primary_account
                        ) VALUES (?, ?, 'TEST', 'Test Bank', 'Concurrency Test Customer', ?, ?, '5678',
                                  'ACTIVE', TRUE)
                        """,
                UUID.randomUUID(),
                customerId,
                "bank-cipher-" + unique,
                "bank-fingerprint-" + unique
        );
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, normalized_email, password_hash, user_type, status, display_name, customer_id
                        ) VALUES (?, ?, ?, 'not-used', 'CUSTOMER', 'ACTIVE', 'Concurrency Test Customer', ?)
                        """,
                userId,
                "concurrency-" + unique + "@meridian.test",
                "concurrency-" + unique + "@meridian.test",
                customerId
        );
        insertVerifiedLink(
                firstLinkId,
                customerId,
                FIRST_PARTNER_COMPANY_ID,
                FIRST_PARTNER_EMPLOYEE_ID,
                FIRST_IMPORT_BATCH_ID,
                "MER-EMP-001"
        );
        insertVerifiedLink(
                secondLinkId,
                customerId,
                SECOND_PARTNER_COMPANY_ID,
                SECOND_PARTNER_EMPLOYEE_ID,
                SECOND_IMPORT_BATCH_ID,
                "AUR-EMP-001"
        );
        return new Fixture(customerId, userId, firstLinkId, secondLinkId);
    }

    private void insertVerifiedLink(
            UUID linkId,
            UUID customerId,
            UUID partnerCompanyId,
            UUID partnerEmployeeId,
            UUID sourceImportBatchId,
            String employeeCode
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO customer_partner_employee_links (
                            id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id,
                            verification_outcome, link_status, verified_identity_ref, verified_employee_code,
                            last_verified_at, last_refreshed_at
                        ) VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                linkId,
                customerId,
                partnerCompanyId,
                partnerEmployeeId,
                sourceImportBatchId,
                "test-identity-" + linkId,
                employeeCode
        );
    }

    private void insertLimit(UUID linkId, BigDecimal totalLimit) {
        jdbcTemplate.update(
                """
                        INSERT INTO salary_advance_limits (
                            id, customer_id, customer_partner_employee_link_id, total_limit,
                            used_amount, reserved_amount, available_amount, status, last_refreshed_at
                        ) VALUES (?, ?, ?, ?, 0, 0, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(),
                fixture.customerId(),
                linkId,
                totalLimit,
                totalLimit
        );
    }

    private UUID salaryAdvanceProductId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM loan_products WHERE product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
    }

    private LoanApplication application(UUID loanProductId, String applicationNumber) {
        return new LoanApplication(
                UUID.randomUUID(),
                fixture.customerId(),
                loanProductId,
                applicationNumber,
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.SUBMITTED,
                REQUESTED_AMOUNT,
                1,
                LocalDateTime.now()
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record Fixture(UUID customerId, UUID userId, UUID firstLinkId, UUID secondLinkId) {
    }

    private record SubmissionOutcome(SalaryAdvanceApplicationDto result, Throwable failure) {

        private static SubmissionOutcome success(SalaryAdvanceApplicationDto result) {
            return new SubmissionOutcome(result, null);
        }

        private static SubmissionOutcome failure(Throwable failure) {
            return new SubmissionOutcome(null, failure);
        }

        private boolean successful() {
            return result != null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCurrentUserConfiguration {

        @Bean
        @Primary
        MutableCurrentUserProvider mutableCurrentUserProvider() {
            return new MutableCurrentUserProvider();
        }
    }

    static class MutableCurrentUserProvider implements CurrentUserProvider {

        private final AtomicReference<AuthenticatedUser> currentUser = new AtomicReference<>();

        void use(UUID userId, UUID customerId) {
            currentUser.set(new AuthenticatedUser(
                    userId,
                    "concurrency-test@meridian.test",
                    "CUSTOMER",
                    customerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:submit")
            ));
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser.get();
        }
    }
}

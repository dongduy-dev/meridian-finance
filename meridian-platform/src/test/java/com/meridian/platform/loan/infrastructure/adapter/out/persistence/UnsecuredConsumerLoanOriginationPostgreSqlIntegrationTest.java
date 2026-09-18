package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartAssistedUnsecuredConsumerLoanUseCase;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                UnsecuredConsumerLoanOriginationPostgreSqlIntegrationTest.UclTestConfiguration.class
        },
        properties = "meridian.loan.offer-expiry.enabled=false"
)
class UnsecuredConsumerLoanOriginationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_ucl_cp1_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UnsecuredConsumerLoanApplicationRequest REQUEST =
            new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("5000000"), 6);

    @Autowired private StartUnsecuredConsumerLoanApplicationUseCase useCase;
    @Autowired private StartAssistedUnsecuredConsumerLoanUseCase assistedUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableCurrentUserProvider currentUserProvider;
    @Autowired private FailingVerificationRepository verificationRepository;

    private UUID customerId;
    private UUID userId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void setUp() {
        verificationRepository.failWrites = false;
        customerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        createReadyCustomer(userId, customerId);
        currentUserProvider.use(userId, customerId);
    }

    @Test
    void persistsCompleteUclCheckpointWithoutPartnerOrSalaryEffects() {
        int partnerLinksBefore = count("SELECT count(*) FROM customer_partner_employee_links");
        int salaryLimitsBefore = count("SELECT count(*) FROM salary_advance_limits");
        int salaryMovementsBefore = count("SELECT count(*) FROM salary_advance_limit_movements");
        int salaryVerificationsBefore = count("SELECT count(*) FROM salary_advance_verifications");

        UnsecuredConsumerLoanApplicationDto result = useCase.startUnsecuredConsumerLoanApplication(REQUEST);

        assertEquals("UNSECURED_CONSUMER_LOAN", result.productCode());
        assertEquals("DOCUMENTS_PENDING", result.status());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());
        assertEquals(1, count("SELECT count(*) FROM loan_applications WHERE id = ? AND customer_id = ? "
                        + "AND product_code = 'UNSECURED_CONSUMER_LOAN' AND product_type = 'UNSECURED' "
                        + "AND status = 'DOCUMENTS_PENDING'",
                result.loanApplicationId(), customerId));
        assertEquals(1, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                        + "WHERE loan_application_id = ? AND product_verification_result = 'PENDING_MANUAL_REVIEW'",
                result.loanApplicationId()));
        assertEquals(3, count("SELECT count(*) FROM document_checklist_items item "
                        + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                        + "WHERE checklist.loan_application_id = ? AND item.requirement_status = 'REQUIRED' "
                        + "AND item.document_type IN ('INCOME_PROOF', 'BANK_STATEMENT', 'EMPLOYMENT_PROOF')",
                result.loanApplicationId()));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? AND from_status IS NULL "
                        + "AND to_status = 'DOCUMENTS_PENDING' AND action = 'SUBMIT_APPLICATION'",
                result.loanApplicationId()));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                        + "WHERE entity_id = ? AND action = 'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED'",
                result.loanApplicationId()));

        assertEquals(partnerLinksBefore, count("SELECT count(*) FROM customer_partner_employee_links"));
        assertEquals(salaryLimitsBefore, count("SELECT count(*) FROM salary_advance_limits"));
        assertEquals(salaryMovementsBefore, count("SELECT count(*) FROM salary_advance_limit_movements"));
        assertEquals(salaryVerificationsBefore, count("SELECT count(*) FROM salary_advance_verifications"));
    }

    @Test
    void v38EnforcesVerificationOwnershipUniquenessAndVocabulary() {
        UnsecuredConsumerLoanApplicationDto result = useCase.startUnsecuredConsumerLoanApplication(REQUEST);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO unsecured_consumer_loan_verifications "
                        + "(loan_application_id, product_verification_result, created_at) "
                        + "VALUES (?, 'PENDING_MANUAL_REVIEW', CURRENT_TIMESTAMP)",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications "
                        + "SET product_verification_result = 'UNSUPPORTED_RESULT' "
                        + "WHERE loan_application_id = ?",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO unsecured_consumer_loan_verifications "
                        + "(loan_application_id, product_verification_result, created_at) "
                        + "VALUES (?, 'PENDING_MANUAL_REVIEW', CURRENT_TIMESTAMP)",
                UUID.randomUUID()
        ));
    }

    @Test
    void rollsBackApplicationChecklistAndHistoryWhenVerificationPersistenceFails() {
        verificationRepository.failWrites = true;

        assertThrows(IllegalStateException.class, () -> useCase.startUnsecuredConsumerLoanApplication(REQUEST));

        assertEquals(0, count("SELECT count(*) FROM loan_applications "
                + "WHERE customer_id = ? AND product_code = 'UNSECURED_CONSUMER_LOAN'", customerId));
        assertEquals(0, count("SELECT count(*) FROM unsecured_consumer_loan_verifications verification "
                + "JOIN loan_applications application ON application.id = verification.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(0, count("SELECT count(*) FROM document_checklists checklist "
                + "JOIN loan_applications application ON application.id = checklist.loan_application_id "
                + "WHERE application.product_code = 'UNSECURED_CONSUMER_LOAN'"));
        assertEquals(0, count("SELECT count(*) FROM audit_events "
                + "WHERE actor_user_id = ? AND action = 'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED'", userId));
    }

    @Test
    void concurrentRequestsCreateOneCompleteBlockingApplication() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<SubmissionOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SubmissionOutcome> first = executor.submit(() -> submitAfter(start));
            Future<SubmissionOutcome> second = executor.submit(() -> submitAfter(start));
            start.countDown();
            outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(SubmissionOutcome::successful).count());
        Throwable failure = outcomes.stream().filter(outcome -> !outcome.successful())
                .map(SubmissionOutcome::failure).findFirst().orElseThrow();
        BusinessStateConflictException conflict = assertInstanceOf(BusinessStateConflictException.class, failure);
        assertEquals("BLOCKING_APPLICATION_EXISTS", conflict.getErrorCode());
        assertEquals(1, count("SELECT count(*) FROM loan_applications "
                + "WHERE customer_id = ? AND product_code = 'UNSECURED_CONSUMER_LOAN'", customerId));
        assertEquals(1, count("SELECT count(*) FROM unsecured_consumer_loan_verifications verification "
                + "JOIN loan_applications application ON application.id = verification.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(3, count("SELECT count(*) FROM document_checklist_items item "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "JOIN loan_applications application ON application.id = checklist.loan_application_id "
                + "WHERE application.customer_id = ? AND application.product_code = 'UNSECURED_CONSUMER_LOAN'",
                customerId));
    }

    @Test
    void assistedConversionRollsBackAndLeavesCaseOpenWhenVerificationPersistenceFails() {
        UUID staffId = createStaff();
        UUID caseId = createAssistedCaseWithPaperApplication(staffId, customerId);
        currentUserProvider.useStaff(staffId);
        verificationRepository.failWrites = true;

        assertThrows(IllegalStateException.class, () -> assistedUseCase.submit(caseId, REQUEST));

        assertEquals(0, count("SELECT count(*) FROM loan_applications "
                + "WHERE customer_id = ? AND product_code = 'UNSECURED_CONSUMER_LOAN'", customerId));
        assertEquals(0, count("SELECT count(*) FROM unsecured_consumer_loan_verifications verification "
                + "JOIN loan_applications application ON application.id = verification.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(0, count("SELECT count(*) FROM document_checklists checklist "
                + "JOIN loan_applications application ON application.id = checklist.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(1, count("SELECT count(*) FROM assisted_origination_cases "
                + "WHERE id = ? AND status = 'OPEN' AND terminal_at IS NULL AND loan_application_id IS NULL", caseId));
        assertEquals(0, count("SELECT count(*) FROM audit_events "
                + "WHERE entity_id = ? AND action = 'ASSISTED_ORIGINATION_CASE_COMPLETED'", caseId));
    }

    @Test
    void concurrentAssistedConversionsOfSameCaseCreateExactlyOneCompleteResult() throws Exception {
        UUID staffId = createStaff();
        UUID caseId = createAssistedCaseWithPaperApplication(staffId, customerId);
        currentUserProvider.useStaff(staffId);
        CountDownLatch start = new CountDownLatch(1);
        List<AssistedSubmissionOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AssistedSubmissionOutcome> first = executor.submit(() -> submitAssistedAfter(start, caseId));
            Future<AssistedSubmissionOutcome> second = executor.submit(() -> submitAssistedAfter(start, caseId));
            start.countDown();
            outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(AssistedSubmissionOutcome::successful).count());
        Throwable failure = outcomes.stream().filter(outcome -> !outcome.successful())
                .map(AssistedSubmissionOutcome::failure).findFirst().orElseThrow();
        BusinessStateConflictException conflict = assertInstanceOf(BusinessStateConflictException.class, failure);
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", conflict.getErrorCode());
        UUID applicationId = outcomes.stream().filter(AssistedSubmissionOutcome::successful)
                .map(AssistedSubmissionOutcome::result).map(AssistedOriginationCaseDto::loanApplicationId)
                .findFirst().orElseThrow();

        assertEquals(1, count("SELECT count(*) FROM loan_applications WHERE id = ? AND customer_id = ? "
                + "AND product_code = 'UNSECURED_CONSUMER_LOAN' AND product_type = 'UNSECURED' "
                + "AND origination_channel = 'STAFF_ASSISTED' AND status = 'DOCUMENTS_PENDING'",
                applicationId, customerId));
        assertEquals(1, count("SELECT count(*) FROM assisted_origination_cases "
                + "WHERE id = ? AND status = 'COMPLETED' AND loan_application_id = ? AND terminal_at IS NOT NULL",
                caseId, applicationId));
        assertEquals(1, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ? AND product_verification_result = 'PENDING_MANUAL_REVIEW'",
                applicationId));
        assertEquals(3, count("SELECT count(*) FROM document_checklist_items item "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "WHERE checklist.loan_application_id = ? AND item.requirement_status = 'REQUIRED' "
                + "AND item.document_type IN ('INCOME_PROOF', 'BANK_STATEMENT', 'EMPLOYMENT_PROOF')",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND from_status IS NULL AND to_status = 'DOCUMENTS_PENDING'",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'ASSISTED_ORIGINATION_CASE_COMPLETED'", caseId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId));
        assertEquals(0, count("SELECT count(*) FROM collateral_loan_verifications WHERE loan_application_id = ?",
                applicationId));
    }

    private SubmissionOutcome submitAfter(CountDownLatch start) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            currentUserProvider.use(userId, customerId);
            return SubmissionOutcome.success(useCase.startUnsecuredConsumerLoanApplication(REQUEST));
        } catch (Throwable failure) {
            return SubmissionOutcome.failure(failure);
        }
    }

    private AssistedSubmissionOutcome submitAssistedAfter(CountDownLatch start, UUID caseId) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return AssistedSubmissionOutcome.success(assistedUseCase.submit(caseId, REQUEST));
        } catch (Throwable failure) {
            return AssistedSubmissionOutcome.failure(failure);
        }
    }

    private UUID createStaff() {
        UUID staffId = UUID.randomUUID();
        String suffix = staffId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'STAFF', 'ACTIVE', 'Assisted UCL Officer', NULL)",
                staffId, "assisted-" + suffix + "@meridian.test", "assisted-" + suffix + "@meridian.test");
        return staffId;
    }

    private UUID createAssistedCaseWithPaperApplication(UUID staffId, UUID selectedCustomerId) {
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO assisted_origination_cases "
                        + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at) "
                        + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', ?, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                caseId, selectedCustomerId, staffId);
        jdbcTemplate.update("INSERT INTO intake_documents "
                        + "(id, assisted_origination_case_id, evidence_type, current_version_id, created_at, updated_at) "
                        + "VALUES (?, ?, 'UCL_PAPER_APPLICATION', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                documentId, caseId);
        jdbcTemplate.update("INSERT INTO intake_document_versions "
                        + "(id, intake_document_id, version_number, upload_request_id, baseline_version_id, "
                        + "original_filename, declared_mime_type, detected_mime_type, byte_size, sha256_hex, "
                        + "storage_key, uploader_staff_user_id, uploaded_at, created_at) "
                        + "VALUES (?, ?, 1, ?, NULL, 'signed-ucl.pdf', 'application/pdf', 'application/pdf', 128, "
                        + "?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                versionId, documentId, UUID.randomUUID(), "a".repeat(64),
                "intake/" + caseId + "/signed-ucl.pdf", staffId);
        jdbcTemplate.update("UPDATE intake_documents SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ?", versionId, documentId);
        return caseId;
    }

    private void createReadyCustomer(UUID createdUserId, UUID createdCustomerId) {
        String suffix = createdCustomerId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                createdCustomerId, "UCL-TEST-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'UCL Test Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), createdCustomerId, "identity-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, account_number_last_four, "
                        + "status, primary_account) "
                        + "VALUES (?, ?, 'TEST', 'Test Bank', 'UCL Test Customer', "
                        + "'protected-test-account', ?, '5678', 'ACTIVE', TRUE)",
                UUID.randomUUID(), createdCustomerId, "account-" + suffix);
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', 'UCL Test Customer', ?)",
                createdUserId, "ucl-" + suffix + "@meridian.test", "ucl-" + suffix + "@meridian.test",
                createdCustomerId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private record SubmissionOutcome(UnsecuredConsumerLoanApplicationDto result, Throwable failure) {
        static SubmissionOutcome success(UnsecuredConsumerLoanApplicationDto result) {
            return new SubmissionOutcome(result, null);
        }

        static SubmissionOutcome failure(Throwable failure) {
            return new SubmissionOutcome(null, failure);
        }

        boolean successful() {
            return result != null;
        }
    }

    private record AssistedSubmissionOutcome(AssistedOriginationCaseDto result, Throwable failure) {
        static AssistedSubmissionOutcome success(AssistedOriginationCaseDto result) {
            return new AssistedSubmissionOutcome(result, null);
        }

        static AssistedSubmissionOutcome failure(Throwable failure) {
            return new AssistedSubmissionOutcome(null, failure);
        }

        boolean successful() {
            return result != null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UclTestConfiguration {

        @Bean
        @Primary
        MutableCurrentUserProvider mutableCurrentUserProvider() {
            return new MutableCurrentUserProvider();
        }

        @Bean
        @Primary
        FailingVerificationRepository failingVerificationRepository(
                UnsecuredConsumerLoanVerificationRepositoryAdapter delegate
        ) {
            return new FailingVerificationRepository(delegate);
        }
    }

    static class MutableCurrentUserProvider implements CurrentUserProvider {
        private final AtomicReference<AuthenticatedUser> currentUser = new AtomicReference<>();

        void use(UUID userId, UUID customerId) {
            currentUser.set(new AuthenticatedUser(
                    userId,
                    "ucl-cp1-test@meridian.test",
                    "CUSTOMER",
                    customerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:submit")
            ));
        }

        void useStaff(UUID userId) {
            currentUser.set(new AuthenticatedUser(
                    userId,
                    "assisted-ucl-test@meridian.test",
                    "STAFF",
                    null,
                    Set.of("LOAN_OFFICER"),
                    Set.of("loan:originate:staff")
            ));
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser.get();
        }
    }

    static class FailingVerificationRepository implements UnsecuredConsumerLoanVerificationRepository {
        private final UnsecuredConsumerLoanVerificationRepository delegate;
        private volatile boolean failWrites;

        FailingVerificationRepository(UnsecuredConsumerLoanVerificationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public UnsecuredConsumerLoanVerification save(UnsecuredConsumerLoanVerification verification) {
            if (failWrites) {
                throw new IllegalStateException("simulated required verification persistence failure");
            }
            return delegate.save(verification);
        }

        @Override
        public Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId) {
            return delegate.findLatestByLoanApplicationId(loanApplicationId);
        }

        @Override
        public Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationIdForUpdate(
                UUID loanApplicationId
        ) {
            return delegate.findLatestByLoanApplicationIdForUpdate(loanApplicationId);
        }
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.application.service.DocumentChecklistService;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.Collateral;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.infrastructure.audit.SpringBusinessAuditPublisher;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
                CollateralLoanOriginationPostgreSqlIntegrationTest.CollateralTestConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CollateralLoanOriginationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_collateral_cp1_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            "target", "collateral-cp1-documents-" + UUID.randomUUID()
    ).toAbsolutePath().toString();
    private static final CollateralLoanApplicationRequest REQUEST = new CollateralLoanApplicationRequest(
            new BigDecimal("25000000"),
            12,
            new CollateralDetailsRequest(
                    CollateralType.MOTORBIKE,
                    "  2024 Honda motorbike  ",
                    new BigDecimal("35000000"),
                    "  Customer-provided ownership statement  ",
                    "  Normal used condition  "
            )
    );

    @Autowired private StartCollateralLoanApplicationUseCase useCase;
    @Autowired private UploadDocumentUseCase uploadDocumentUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableCurrentUserProvider currentUserProvider;
    @Autowired private FailingCollateralRepository collateralRepository;
    @Autowired private FailingChecklistPort checklistPort;
    @Autowired private FailingVerificationRepository verificationRepository;
    @Autowired private FailingTransitionRepository transitionRepository;
    @Autowired private FailingAuditPublisher auditPublisher;

    private UUID customerId;
    private UUID userId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
        registry.add("meridian.document.storage-root", () -> STORAGE_ROOT);
    }

    @BeforeEach
    void setUp() {
        collateralRepository.failWrites = false;
        checklistPort.failWrites = false;
        verificationRepository.failWrites = false;
        transitionRepository.failWrites = false;
        auditPublisher.failCollateralSubmission = false;
        customerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        createReadyCustomer(userId, customerId);
        currentUserProvider.use(userId, customerId);
    }

    @Test
    void persistsCompleteCheckpointAndReturnedChecklistItemSupportsExistingUploadFlow() {
        int partnerLinksBefore = count("SELECT count(*) FROM customer_partner_employee_links");
        int salaryLimitsBefore = count("SELECT count(*) FROM salary_advance_limits");
        int salaryMovementsBefore = count("SELECT count(*) FROM salary_advance_limit_movements");
        int salaryVerificationsBefore = count("SELECT count(*) FROM salary_advance_verifications");

        CollateralLoanApplicationDto result = useCase.startCollateralLoanApplication(REQUEST);
        UUID checklistItemId = result.evidenceRequirements().getFirst().checklistItemId();

        assertEquals("COLLATERAL_LOAN", result.productCode());
        assertEquals("SECURED", result.productType());
        assertEquals("DOCUMENTS_PENDING", result.status());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());
        assertEquals("COLLATERAL_OWNERSHIP_EVIDENCE",
                result.evidenceRequirements().getFirst().documentType());
        assertEquals("REQUIRED", result.evidenceRequirements().getFirst().requirementStatus());
        assertEquals(1, count("SELECT count(*) FROM loan_applications WHERE id = ? AND customer_id = ? "
                        + "AND product_code = 'COLLATERAL_LOAN' AND product_type = 'SECURED' "
                        + "AND status = 'DOCUMENTS_PENDING'",
                result.loanApplicationId(), customerId));
        assertEquals(1, count("SELECT count(*) FROM collaterals WHERE loan_application_id = ? "
                        + "AND collateral_type = 'MOTORBIKE' AND description = '2024 Honda motorbike' "
                        + "AND estimated_value = 35000000 AND ownership_status = ? "
                        + "AND condition_note = 'Normal used condition'",
                result.loanApplicationId(), "Customer-provided ownership statement"));
        assertEquals(1, count("SELECT count(*) FROM collateral_loan_verifications "
                        + "WHERE loan_application_id = ? AND product_verification_result = 'PENDING_MANUAL_REVIEW'",
                result.loanApplicationId()));
        assertEquals(1, count("SELECT count(*) FROM document_checklist_items item "
                        + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                        + "WHERE item.id = ? AND checklist.loan_application_id = ? "
                        + "AND item.document_type = 'COLLATERAL_OWNERSHIP_EVIDENCE' "
                        + "AND item.requirement_status = 'REQUIRED'",
                checklistItemId, result.loanApplicationId()));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? AND from_status IS NULL "
                        + "AND to_status = 'DOCUMENTS_PENDING' AND action = 'SUBMIT_APPLICATION'",
                result.loanApplicationId()));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                        + "AND action = 'COLLATERAL_LOAN_APPLICATION_SUBMITTED' AND payload = '{}'::jsonb",
                result.loanApplicationId()));

        uploadDocumentUseCase.upload(new UploadDocumentCommand(
                result.loanApplicationId(),
                checklistItemId,
                UUID.randomUUID(),
                null,
                "ownership-evidence.pdf",
                "application/pdf",
                new ByteArrayInputStream("%PDF-1.4 collateral-test".getBytes(StandardCharsets.UTF_8)),
                DocumentUploaderActorType.CUSTOMER,
                userId,
                customerId
        ));
        assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?",
                String.class,
                result.loanApplicationId()
        ));

        assertEquals(partnerLinksBefore, count("SELECT count(*) FROM customer_partner_employee_links"));
        assertEquals(salaryLimitsBefore, count("SELECT count(*) FROM salary_advance_limits"));
        assertEquals(salaryMovementsBefore, count("SELECT count(*) FROM salary_advance_limit_movements"));
        assertEquals(salaryVerificationsBefore, count("SELECT count(*) FROM salary_advance_verifications"));
    }

    @Test
    void v44EnforcesFactsVerificationAndEvidenceVocabularyWithoutOneAssetDatabaseLimit() {
        CollateralLoanApplicationDto result = useCase.startCollateralLoanApplication(REQUEST);

        jdbcTemplate.update("INSERT INTO collaterals "
                        + "(loan_application_id, collateral_type, description, estimated_value, "
                        + "ownership_status, condition_note, created_at) "
                        + "VALUES (?, 'ELECTRONICS', 'Second future-compatible asset', 1000000, "
                        + "'Customer statement', 'Used condition', CURRENT_TIMESTAMP)",
                result.loanApplicationId());
        assertEquals(2, count("SELECT count(*) FROM collaterals WHERE loan_application_id = ?",
                result.loanApplicationId()));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO collaterals (loan_application_id, collateral_type, description, estimated_value, "
                        + "ownership_status, condition_note, created_at) "
                        + "VALUES (?, 'INVALID', 'Asset', 1, 'Owner', 'Condition', CURRENT_TIMESTAMP)",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO collaterals (loan_application_id, collateral_type, description, estimated_value, "
                        + "ownership_status, condition_note, created_at) "
                        + "VALUES (?, 'OTHER', ' Asset ', 1.5, 'Owner', 'Condition', CURRENT_TIMESTAMP)",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO collateral_loan_verifications "
                        + "(loan_application_id, product_verification_result, created_at) "
                        + "VALUES (?, 'PENDING_MANUAL_REVIEW', CURRENT_TIMESTAMP)",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE collateral_loan_verifications SET product_verification_result = 'VERIFIED' "
                        + "WHERE loan_application_id = ?",
                result.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO collateral_loan_verifications "
                        + "(loan_application_id, product_verification_result, created_at) "
                        + "VALUES (?, 'PENDING_MANUAL_REVIEW', CURRENT_TIMESTAMP)",
                UUID.randomUUID()
        ));
    }

    @Test
    void everyRequiredWriteFailureRollsBackTheWholeOrigination() {
        assertRollbackWhen(() -> collateralRepository.failWrites = true);
        assertRollbackWhen(() -> checklistPort.failWrites = true);
        assertRollbackWhen(() -> verificationRepository.failWrites = true);
        assertRollbackWhen(() -> transitionRepository.failWrites = true);
        assertRollbackWhen(() -> auditPublisher.failCollateralSubmission = true);
    }

    @Test
    void concurrentRequestsCreateAtMostOneCompleteBlockingApplication() throws Exception {
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
                + "WHERE customer_id = ? AND product_code = 'COLLATERAL_LOAN'", customerId));
        assertEquals(1, count("SELECT count(*) FROM collaterals collateral "
                + "JOIN loan_applications application ON application.id = collateral.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(1, count("SELECT count(*) FROM collateral_loan_verifications verification "
                + "JOIN loan_applications application ON application.id = verification.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(1, count("SELECT count(*) FROM document_checklist_items item "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "JOIN loan_applications application ON application.id = checklist.loan_application_id "
                + "WHERE application.customer_id = ? AND application.product_code = 'COLLATERAL_LOAN'",
                customerId));
    }

    private void assertRollbackWhen(Runnable configureFailure) {
        resetFailures();
        configureFailure.run();
        assertThrows(RuntimeException.class, () -> useCase.startCollateralLoanApplication(REQUEST));
        assertNoOriginationRows();
        resetFailures();
    }

    private void assertNoOriginationRows() {
        assertEquals(0, count("SELECT count(*) FROM loan_applications "
                + "WHERE customer_id = ? AND product_code = 'COLLATERAL_LOAN'", customerId));
        assertEquals(0, count("SELECT count(*) FROM collaterals collateral "
                + "JOIN loan_applications application ON application.id = collateral.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(0, count("SELECT count(*) FROM collateral_loan_verifications verification "
                + "JOIN loan_applications application ON application.id = verification.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(0, count("SELECT count(*) FROM document_checklists checklist "
                + "JOIN loan_applications application ON application.id = checklist.loan_application_id "
                + "WHERE application.customer_id = ?", customerId));
        assertEquals(0, count("SELECT count(*) FROM audit_events WHERE actor_user_id = ? "
                + "AND action = 'COLLATERAL_LOAN_APPLICATION_SUBMITTED'", userId));
    }

    private void resetFailures() {
        collateralRepository.failWrites = false;
        checklistPort.failWrites = false;
        verificationRepository.failWrites = false;
        transitionRepository.failWrites = false;
        auditPublisher.failCollateralSubmission = false;
    }

    private SubmissionOutcome submitAfter(CountDownLatch start) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            currentUserProvider.use(userId, customerId);
            return SubmissionOutcome.success(useCase.startCollateralLoanApplication(REQUEST));
        } catch (Throwable failure) {
            return SubmissionOutcome.failure(failure);
        }
    }

    private void createReadyCustomer(UUID createdUserId, UUID createdCustomerId) {
        String suffix = createdCustomerId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                createdCustomerId, "CL-TEST-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'Collateral Test Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), createdCustomerId, "identity-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, account_number_last_four, "
                        + "status, primary_account) "
                        + "VALUES (?, ?, 'TEST', 'Test Bank', 'Collateral Test Customer', "
                        + "'protected-test-account', ?, '5678', 'ACTIVE', TRUE)",
                UUID.randomUUID(), createdCustomerId, "account-" + suffix);
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', "
                        + "'Collateral Test Customer', ?)",
                createdUserId, "cl-" + suffix + "@meridian.test", "cl-" + suffix + "@meridian.test",
                createdCustomerId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private record SubmissionOutcome(CollateralLoanApplicationDto result, Throwable failure) {
        static SubmissionOutcome success(CollateralLoanApplicationDto result) {
            return new SubmissionOutcome(result, null);
        }

        static SubmissionOutcome failure(Throwable failure) {
            return new SubmissionOutcome(null, failure);
        }

        boolean successful() {
            return result != null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CollateralTestConfiguration {

        @Bean
        @Primary
        MutableCurrentUserProvider mutableCurrentUserProvider() {
            return new MutableCurrentUserProvider();
        }

        @Bean
        @Primary
        FailingCollateralRepository failingCollateralRepository(CollateralRepositoryAdapter delegate) {
            return new FailingCollateralRepository(delegate);
        }

        @Bean
        @Primary
        FailingChecklistPort failingChecklistPort(DocumentChecklistService delegate) {
            return new FailingChecklistPort(delegate);
        }

        @Bean
        @Primary
        FailingVerificationRepository failingVerificationRepository(
                CollateralLoanVerificationRepositoryAdapter delegate
        ) {
            return new FailingVerificationRepository(delegate);
        }

        @Bean
        @Primary
        FailingTransitionRepository failingTransitionRepository(
                LoanApplicationStatusTransitionRepositoryAdapter delegate
        ) {
            return new FailingTransitionRepository(delegate);
        }

        @Bean
        @Primary
        FailingAuditPublisher failingAuditPublisher(SpringBusinessAuditPublisher delegate) {
            return new FailingAuditPublisher(delegate);
        }
    }

    static class MutableCurrentUserProvider implements CurrentUserProvider {
        private final AtomicReference<AuthenticatedUser> currentUser = new AtomicReference<>();

        void use(UUID currentUserId, UUID currentCustomerId) {
            currentUser.set(new AuthenticatedUser(
                    currentUserId,
                    "collateral-cp1-test@meridian.test",
                    "CUSTOMER",
                    currentCustomerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:submit")
            ));
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser.get();
        }
    }

    static class FailingCollateralRepository implements CollateralRepository {
        private final CollateralRepository delegate;
        private volatile boolean failWrites;

        FailingCollateralRepository(CollateralRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Collateral save(Collateral collateral) {
            if (failWrites) {
                throw new IllegalStateException("simulated Collateral persistence failure");
            }
            return delegate.save(collateral);
        }
    }

    static class FailingChecklistPort implements LoanDocumentChecklistPort {
        private final LoanDocumentChecklistPort delegate;
        private volatile boolean failWrites;

        FailingChecklistPort(LoanDocumentChecklistPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
            return delegate.resolveSubmissionInitialState(productCode);
        }

        @Override
        public SubmissionChecklistSnapshot createSubmissionChecklist(
                UUID loanApplicationId,
                ProductCode productCode,
                com.meridian.platform.shared.application.operation.BusinessOperationContext operationContext
        ) {
            if (failWrites) {
                throw new IllegalStateException("simulated checklist persistence failure");
            }
            return delegate.createSubmissionChecklist(loanApplicationId, productCode, operationContext);
        }

        @Override
        public boolean isProcessingReady(UUID loanApplicationId) {
            return delegate.isProcessingReady(loanApplicationId);
        }
    }

    static class FailingVerificationRepository implements CollateralLoanVerificationRepository {
        private final CollateralLoanVerificationRepository delegate;
        private volatile boolean failWrites;

        FailingVerificationRepository(CollateralLoanVerificationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public CollateralLoanVerification save(CollateralLoanVerification verification) {
            if (failWrites) {
                throw new IllegalStateException("simulated verification persistence failure");
            }
            return delegate.save(verification);
        }

        @Override
        public Optional<CollateralLoanVerification> findByLoanApplicationId(UUID loanApplicationId) {
            return delegate.findByLoanApplicationId(loanApplicationId);
        }
    }

    static class FailingTransitionRepository implements LoanApplicationStatusTransitionRepository {
        private final LoanApplicationStatusTransitionRepository delegate;
        private volatile boolean failWrites;

        FailingTransitionRepository(LoanApplicationStatusTransitionRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public int nextSequenceNumber(UUID loanApplicationId) {
            return delegate.nextSequenceNumber(loanApplicationId);
        }

        @Override
        public LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition) {
            if (failWrites) {
                throw new IllegalStateException("simulated history persistence failure");
            }
            return delegate.save(transition);
        }

        @Override
        public long countMatching(
                UUID loanApplicationId,
                LoanApplicationStatus fromStatus,
                LoanApplicationStatus toStatus,
                LoanApplicationTransitionAction action
        ) {
            return delegate.countMatching(loanApplicationId, fromStatus, toStatus, action);
        }
    }

    static class FailingAuditPublisher implements BusinessAuditPublisher {
        private final BusinessAuditPublisher delegate;
        private volatile boolean failCollateralSubmission;

        FailingAuditPublisher(BusinessAuditPublisher delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(BusinessAuditEvent event) {
            if (failCollateralSubmission && event.entries().stream().anyMatch(
                    entry -> entry.action() == BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED
            )) {
                throw new IllegalStateException("simulated audit persistence failure");
            }
            delegate.publish(event);
        }
    }
}

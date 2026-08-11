package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                UnsecuredConsumerLoanManualVerificationPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class UnsecuredConsumerLoanManualVerificationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_ucl_cp2_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), TEST_SCHEMA + "_documents"
    ).toString();
    private static final UUID LOAN_OFFICER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID SECOND_STAFF_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final byte[] PDF = "%PDF-1.7\n% Meridian UCL CP2 evidence\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private StartUnsecuredConsumerLoanApplicationUseCase submissionUseCase;
    @Autowired private UploadDocumentUseCase uploadUseCase;
    @Autowired private ReviewDocumentUseCase documentReviewUseCase;
    @Autowired private ManageUnsecuredConsumerLoanVerificationUseCase verificationUseCase;
    @Autowired private StartLoanApplicationReviewUseCase reviewStartUseCase;
    @Autowired private SubmitReviewRecommendationUseCase recommendationUseCase;
    @Autowired private SubmitApprovalDecisionUseCase decisionUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ThreadLocalCurrentUserProvider currentUserProvider;
    @MockitoSpyBean private BusinessAuditPublisher auditPublisher;

    private Fixture fixture;

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
        reset(auditPublisher);
        fixture = createReadyCustomer();
        useCustomer();
    }

    @AfterEach
    void clearUser() {
        currentUserProvider.clear();
    }

    @Test
    void documentBackedUclLifecycleReachesApprovalPendingAndBlocksApprovalWithoutOffer() {
        UUID applicationId = originateAndMakeProcessingReady();

        useLoanOfficer();
        UnsecuredConsumerLoanVerificationDto started = verificationUseCase
                .startManualVerification(applicationId);
        assertEquals("VERIFICATION_PENDING", started.status());
        assertEquals("PENDING_MANUAL_REVIEW", started.productVerificationResult());

        UnsecuredConsumerLoanVerificationDto completed = verificationUseCase
                .completeManualVerification(
                        applicationId,
                        new CompleteUnsecuredConsumerLoanVerificationRequest(
                                "Income and employment evidence are consistent for Loan Officer review."
                        )
                );
        assertEquals("SUBMITTED", completed.status());
        assertEquals("VERIFIED", completed.productVerificationResult());
        assertEquals(LOAN_OFFICER_USER_ID, uuid(
                "SELECT reviewed_by_user_id FROM unsecured_consumer_loan_verifications "
                        + "WHERE loan_application_id = ?",
                applicationId
        ));
        assertTrue(Math.abs(java.time.temporal.ChronoUnit.NANOS.between(
                completed.reviewedAt(),
                timestamp("SELECT reviewed_at FROM unsecured_consumer_loan_verifications "
                        + "WHERE loan_application_id = ?", applicationId)
        )) <= 1_000);

        assertEquals("UNDER_REVIEW", reviewStartUseCase.startReview(applicationId).status());
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null)
        );
        assertEquals("APPROVAL_PENDING", status(applicationId));

        useApprover();
        BusinessStateConflictException approvalFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> decisionUseCase.submitApprovalDecision(
                        applicationId,
                        new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
                )
        );
        assertEquals("UCL_OFFER_EXECUTION_NOT_READY", approvalFailure.getErrorCode());
        assertEquals("APPROVAL_PENDING", status(applicationId));
        assertEquals(0, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'APPROVAL_DECISION_RECORDED'"));
        assertEquals(1, count("SELECT count(*) FROM review_recommendations WHERE loan_application_id = ?",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'START_PRODUCT_VERIFICATION'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'", applicationId));
    }

    @Test
    void twoConcurrentVerificationStartsProduceOneEffectiveTransition() throws Exception {
        UUID applicationId = originateAndMakeProcessingReady();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> startVerificationAfter(
                    applicationId, LOAN_OFFICER_USER_ID, ready, start));
            Future<CommandOutcome> second = executor.submit(() -> startVerificationAfter(
                    applicationId, SECOND_STAFF_USER_ID, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        BusinessStateConflictException failure = assertInstanceOf(
                BusinessStateConflictException.class,
                outcomes.stream().filter(outcome -> !outcome.successful())
                        .map(CommandOutcome::failure).findFirst().orElseThrow()
        );
        assertEquals("PRODUCT_VERIFICATION_START_NOT_ALLOWED", failure.getErrorCode());
        assertEquals("VERIFICATION_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'START_PRODUCT_VERIFICATION'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED'", applicationId));
    }

    @Test
    void twoConcurrentVerificationCompletionsProduceOneAuthoritativeDecision() throws Exception {
        UUID applicationId = originateAndMakeProcessingReady();
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> completeVerificationAfter(
                    applicationId, LOAN_OFFICER_USER_ID, "First Staff assessment.", ready, start));
            Future<CommandOutcome> second = executor.submit(() -> completeVerificationAfter(
                    applicationId, SECOND_STAFF_USER_ID, "Second Staff assessment.", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        BusinessStateConflictException failure = assertInstanceOf(
                BusinessStateConflictException.class,
                outcomes.stream().filter(outcome -> !outcome.successful())
                        .map(CommandOutcome::failure).findFirst().orElseThrow()
        );
        assertEquals("PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED", failure.getErrorCode());
        assertEquals("SUBMITTED", status(applicationId));
        assertEquals("VERIFIED", text("SELECT product_verification_result "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?", applicationId));
        UUID persistedReviewer = uuid("SELECT reviewed_by_user_id FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId);
        assertTrue(Set.of(LOAN_OFFICER_USER_ID, SECOND_STAFF_USER_ID).contains(persistedReviewer));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED'", applicationId));
    }

    @Test
    void verificationCompletionRacingReviewStartNeverStartsReviewBeforeVerification() throws Exception {
        UUID applicationId = originateAndMakeProcessingReady();
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CommandOutcome completion;
        CommandOutcome review;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> completionFuture = executor.submit(() -> completeVerificationAfter(
                    applicationId, LOAN_OFFICER_USER_ID, "Concurrent completion assessment.", ready, start));
            Future<CommandOutcome> reviewFuture = executor.submit(() -> startReviewAfter(
                    applicationId, SECOND_STAFF_USER_ID, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            completion = completionFuture.get(15, TimeUnit.SECONDS);
            review = reviewFuture.get(15, TimeUnit.SECONDS);
        }

        assertTrue(completion.successful());
        assertEquals("VERIFIED", text("SELECT product_verification_result "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?", applicationId));
        if (review.successful()) {
            assertEquals("UNDER_REVIEW", status(applicationId));
            assertEquals(1, count("SELECT count(*) FROM loan_application_review_cycles "
                    + "WHERE loan_application_id = ?", applicationId));
        } else {
            BusinessRuleViolationException failure = assertInstanceOf(
                    BusinessRuleViolationException.class, review.failure());
            assertEquals("PRODUCT_VERIFICATION_PENDING", failure.getErrorCode());
            assertEquals("SUBMITTED", status(applicationId));
            assertEquals(0, count("SELECT count(*) FROM loan_application_review_cycles "
                    + "WHERE loan_application_id = ?", applicationId));
        }
    }

    @Test
    void failedMandatoryCompletionAuditRollsBackVerificationApplicationHistoryAndAudit() {
        UUID applicationId = originateAndMakeProcessingReady();
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        int auditBefore = count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED'", applicationId);

        doThrow(new IllegalStateException("simulated mandatory completion audit failure"))
                .when(auditPublisher)
                .publish(argThat(this::containsCompletionAudit));

        assertThrows(IllegalStateException.class, () -> verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest("Rollback assessment evidence.")
        ));

        assertEquals("VERIFICATION_PENDING", status(applicationId));
        assertEquals("PENDING_MANUAL_REVIEW", text("SELECT product_verification_result "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?", applicationId));
        assertNull(nullableUuid("SELECT reviewed_by_user_id FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertNull(timestamp("SELECT reviewed_at FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertNull(text("SELECT assessment_note FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'", applicationId));
        assertEquals(auditBefore, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED'", applicationId));
    }

    @Test
    void v39PreservesPendingRowsRejectsPartialEvidenceAndRemainsForwardCompatible() {
        UnsecuredConsumerLoanApplicationDto application = submissionUseCase
                .startUnsecuredConsumerLoanApplication(
                        new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("5000000"), 6)
                );

        assertEquals("PENDING_MANUAL_REVIEW", text("SELECT product_verification_result "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?",
                application.loanApplicationId()));
        assertNull(nullableUuid("SELECT reviewed_by_user_id FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", application.loanApplicationId()));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications SET reviewed_by_user_id = ? "
                        + "WHERE loan_application_id = ?",
                LOAN_OFFICER_USER_ID,
                application.loanApplicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications SET product_verification_result = 'VERIFIED' "
                        + "WHERE loan_application_id = ?",
                application.loanApplicationId()
        ));

        assertEquals(1, jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications SET product_verification_result = 'FAILED' "
                        + "WHERE loan_application_id = ?",
                application.loanApplicationId()
        ));
    }

    private UUID originateAndMakeProcessingReady() {
        useCustomer();
        UnsecuredConsumerLoanApplicationDto application = submissionUseCase
                .startUnsecuredConsumerLoanApplication(
                        new UnsecuredConsumerLoanApplicationRequest(new BigDecimal("5000000"), 6)
                );
        List<UUID> checklistItemIds = jdbcTemplate.query(
                "SELECT item.id FROM document_checklist_items item "
                        + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                        + "WHERE checklist.loan_application_id = ? ORDER BY item.document_type",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                application.loanApplicationId()
        );
        assertEquals(3, checklistItemIds.size());

        List<UploadedEvidence> uploaded = checklistItemIds.stream()
                .map(itemId -> new UploadedEvidence(itemId, upload(application.loanApplicationId(), itemId)))
                .toList();
        assertEquals("SUBMITTED", status(application.loanApplicationId()));

        useLoanOfficer();
        for (UploadedEvidence evidence : uploaded) {
            documentReviewUseCase.review(new ReviewDocumentCommand(
                    application.loanApplicationId(),
                    evidence.checklistItemId(),
                    evidence.version().documentVersionId(),
                    UUID.randomUUID(),
                    DocumentReviewOutcome.ACCEPT_DOCUMENT,
                    null,
                    "Restricted UCL evidence acceptance note.",
                    LOAN_OFFICER_USER_ID,
                    false
            ));
        }
        assertEquals(3, count("SELECT count(*) FROM document_checklist_items item "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "WHERE checklist.loan_application_id = ? AND item.current_review_decision_id IS NOT NULL",
                application.loanApplicationId()));
        return application.loanApplicationId();
    }

    private DocumentVersionDto upload(UUID applicationId, UUID checklistItemId) {
        return uploadUseCase.upload(new UploadDocumentCommand(
                applicationId,
                checklistItemId,
                UUID.randomUUID(),
                null,
                "ucl-evidence.pdf",
                "application/pdf",
                new ByteArrayInputStream(PDF),
                DocumentUploaderActorType.CUSTOMER,
                fixture.customerUserId(),
                fixture.customerId()
        ));
    }

    private CommandOutcome startVerificationAfter(
            UUID applicationId,
            UUID actorId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useStaff(actorId);
        return afterBarrier(ready, start, () -> verificationUseCase.startManualVerification(applicationId));
    }

    private CommandOutcome completeVerificationAfter(
            UUID applicationId,
            UUID actorId,
            String note,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useStaff(actorId);
        return afterBarrier(ready, start, () -> verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest(note)
        ));
    }

    private CommandOutcome startReviewAfter(
            UUID applicationId,
            UUID actorId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useStaff(actorId);
        return afterBarrier(ready, start, () -> reviewStartUseCase.startReview(applicationId));
    }

    private CommandOutcome afterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            Command command
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent UCL command did not start.");
            }
            command.execute();
            return CommandOutcome.success();
        } catch (RuntimeException exception) {
            return CommandOutcome.failure(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CommandOutcome.failure(new IllegalStateException("Concurrent UCL command was interrupted.", exception));
        } finally {
            currentUserProvider.clear();
        }
    }

    private boolean containsCompletionAudit(BusinessAuditEvent event) {
        return event != null && event.entries().stream().anyMatch(
                entry -> entry.action() == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED
        );
    }

    private Fixture createReadyCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "UCL-CP2-" + unique.substring(0, 12));
        jdbcTemplate.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'UCL CP2 Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), customerId, "identity-" + unique);
        jdbcTemplate.update("INSERT INTO customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, account_number_last_four, "
                        + "status, primary_account) "
                        + "VALUES (?, ?, 'TEST', 'Test Bank', 'UCL CP2 Customer', "
                        + "'protected-test-account', ?, '5678', 'ACTIVE', TRUE)",
                UUID.randomUUID(), customerId, "account-" + unique);
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', 'UCL CP2 Customer', ?)",
                customerUserId,
                "ucl-cp2-" + unique + "@meridian.test",
                "ucl-cp2-" + unique + "@meridian.test",
                customerId);
        return new Fixture(customerId, customerUserId);
    }

    private void useCustomer() {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(),
                "ucl-cp2-customer@meridian.test",
                "CUSTOMER",
                fixture.customerId(),
                Set.of("CUSTOMER"),
                Set.of("loan:submit", "document:upload:own")
        ));
    }

    private void useLoanOfficer() {
        useStaff(LOAN_OFFICER_USER_ID);
    }

    private void useApprover() {
        currentUserProvider.use(new AuthenticatedUser(
                APPROVER_USER_ID,
                "approver@meridian.local",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("approval:decide")
        ));
    }

    private void useStaff(UUID userId) {
        currentUserProvider.use(new AuthenticatedUser(
                userId,
                "ucl-cp2-staff@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review", "approval:recommend", "document:review")
        ));
    }

    private String status(UUID applicationId) {
        return text("SELECT status FROM loan_applications WHERE id = ?", applicationId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private UUID uuid(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, UUID.class, arguments);
    }

    private UUID nullableUuid(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), arguments);
    }

    private java.time.LocalDateTime timestamp(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, java.time.LocalDateTime.class, arguments);
    }

    private record Fixture(UUID customerId, UUID customerUserId) {
    }

    private record UploadedEvidence(UUID checklistItemId, DocumentVersionDto version) {
    }

    private record CommandOutcome(RuntimeException failure) {
        static CommandOutcome success() {
            return new CommandOutcome(null);
        }

        static CommandOutcome failure(RuntimeException failure) {
            return new CommandOutcome(failure);
        }

        boolean successful() {
            return failure == null;
        }
    }

    @FunctionalInterface
    private interface Command {
        void execute();
    }

    static final class ThreadLocalCurrentUserProvider implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> current = new ThreadLocal<>();

        @Override
        public AuthenticatedUser currentUser() {
            AuthenticatedUser user = current.get();
            if (user == null) {
                throw new IllegalStateException("No test user is active.");
            }
            return user;
        }

        void use(AuthenticatedUser user) {
            current.set(user);
        }

        void clear() {
            current.remove();
        }
    }

    @TestConfiguration
    static class TestCurrentUserConfiguration {
        @Bean
        @Primary
        ThreadLocalCurrentUserProvider currentUserProvider() {
            return new ThreadLocalCurrentUserProvider();
        }
    }
}

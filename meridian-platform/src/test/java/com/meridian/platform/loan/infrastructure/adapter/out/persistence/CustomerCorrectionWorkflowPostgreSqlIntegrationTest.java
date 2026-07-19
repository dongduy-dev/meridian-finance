package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.document.application.dto.DocumentReviewQueueItemDto;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.QueryDocumentReviewQueueUseCase;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                CustomerCorrectionWorkflowPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CustomerCorrectionWorkflowPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_customer_correction_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), TEST_SCHEMA + "_documents").toString();
    private static final UUID PARTNER_COMPANY_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARTNER_EMPLOYEE_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private static final UUID IMPORT_BATCH_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID LOAN_OFFICER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final byte[] PDF = "%PDF-1.7\n% Meridian test evidence\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private StartSalaryAdvanceApplicationUseCase submissionUseCase;
    @Autowired private StartLoanApplicationReviewUseCase reviewStartUseCase;
    @Autowired private SubmitReviewRecommendationUseCase recommendationUseCase;
    @Autowired private SubmitApprovalDecisionUseCase decisionUseCase;
    @Autowired private UploadDocumentUseCase uploadUseCase;
    @Autowired private QueryDocumentReviewQueueUseCase documentReviewQueueUseCase;
    @Autowired private ReviewDocumentUseCase documentReviewUseCase;
    @Autowired private QueryOwnCorrectionTasksUseCase customerTaskQuery;
    @Autowired private CompleteOwnCorrectionTaskUseCase customerTaskCompletion;
    @Autowired private ResubmitOwnCorrectionUseCase customerResubmission;
    @Autowired private RespondToApprovedOfferUseCase offerResponseUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ThreadLocalCurrentUserProvider currentUserProvider;

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
        fixture = createFixture();
        useCustomer();
    }

    @AfterEach
    void clearUser() {
        currentUserProvider.clear();
    }

    @Test
    void customerRevisionReplacementAndNewReviewCycleReachContractPending() {
        SalaryAdvanceApplicationDto application = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), money(3_000_000), 1)
        );
        UUID applicationId = application.loanApplicationId();
        assertEquals("SUBMITTED", status(applicationId));
        assertEquals(0, count("SELECT count(*) FROM document_checklist_items item "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "WHERE checklist.loan_application_id = ?", applicationId));

        useLoanOfficer();
        reviewStartUseCase.startReview(applicationId);
        UUID cycle1 = activeCycle(applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                        null,
                        "Restricted approval note.",
                        cycle1,
                        CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                        new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                                CorrectionResponsibility.CUSTOMER,
                                DocumentType.RECENT_PAYSLIP,
                                true,
                                null,
                                null,
                                "Upload a recent payslip for clarification.",
                                null
                        )))
                )
        );
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals("CORRECTION_REQUIRED", cycleStatus(cycle1));

        useCustomer();
        CustomerCorrectionTaskDto uploadTask = onlyTask(applicationId);
        DocumentVersionDto version1 = upload(applicationId, uploadTask.checklistItemId(), null, "payslip.pdf");
        customerTaskCompletion.complete(
                applicationId, uploadTask.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        UUID firstResubmissionId = UUID.randomUUID();
        assertEquals("SUBMITTED", customerResubmission.resubmit(
                applicationId, new CorrectionResubmissionRequest(firstResubmissionId)).loanApplicationStatus());
        assertEquals("SUBMITTED", customerResubmission.resubmit(
                applicationId, new CorrectionResubmissionRequest(firstResubmissionId)).loanApplicationStatus());
        assertEquals("CORRECTED", cycleStatus(cycle1));

        useLoanOfficer();
        List<DocumentReviewQueueItemDto> queue = documentReviewQueueUseCase.findAwaitingReview(0, 20);
        assertTrue(queue.stream().anyMatch(item -> item.currentVersionId().equals(version1.documentVersionId())));
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                uploadTask.checklistItemId(),
                version1.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.REQUEST_REPLACEMENT,
                null,
                "Restricted reviewer note.",
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                "Replace the payslip with a clearer copy.",
                LOAN_OFFICER_USER_ID,
                false
        ));
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));

        useCustomer();
        CustomerCorrectionTaskDto replacementTask = onlyTask(applicationId);
        DocumentVersionDto version2 = upload(
                applicationId, replacementTask.checklistItemId(), version1.documentVersionId(), "payslip-v2.pdf");
        customerTaskCompletion.complete(
                applicationId, replacementTask.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );

        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                replacementTask.checklistItemId(),
                version2.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted acceptance note.",
                LOAN_OFFICER_USER_ID,
                false
        ));

        useCustomer();
        assertEquals("UNDER_REVIEW", customerResubmission.resubmit(
                applicationId, new CorrectionResubmissionRequest(UUID.randomUUID())).loanApplicationStatus());
        UUID cycle2 = activeCycle(applicationId);
        assertEquals(2, cycleNumber(cycle2));
        assertEquals(3, count("SELECT count(*) FROM salary_advance_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ? AND movement_type = 'RESERVED'", applicationId));

        useLoanOfficer();
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null)
        );
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        useCustomer();
        assertEquals(ApprovedOfferActionOutcome.SUCCESS, offerResponseUseCase.acceptOffer(applicationId).outcome());
        assertEquals("CONTRACT_PENDING", status(applicationId));
        assertEquals("COMPLETED", cycleStatus(cycle2));

        assertEquals(2, count("SELECT count(*) FROM document_versions version "
                + "JOIN documents document ON document.id = version.document_id "
                + "JOIN document_checklist_items item ON item.id = document.checklist_item_id "
                + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                + "WHERE checklist.loan_application_id = ?", applicationId));
        assertEquals(2, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ? AND status = 'RESUBMITTED'", applicationId));
        assertEquals(0, count("SELECT count(*) FROM audit_events WHERE payload::text LIKE '%payslip%' "
                + "OR payload::text LIKE '%Restricted%'"));
    }

    private DocumentVersionDto upload(
            UUID applicationId, UUID checklistItemId, UUID expectedVersionId, String filename
    ) {
        return uploadUseCase.upload(new UploadDocumentCommand(
                applicationId,
                checklistItemId,
                UUID.randomUUID(),
                expectedVersionId,
                filename,
                "application/pdf",
                new ByteArrayInputStream(PDF),
                DocumentUploaderActorType.CUSTOMER,
                fixture.customerUserId(),
                fixture.customerId()
        ));
    }

    private CustomerCorrectionTaskDto onlyTask(UUID applicationId) {
        List<CustomerCorrectionTaskDto> tasks = customerTaskQuery.findOwnTasks(applicationId);
        List<CustomerCorrectionTaskDto> open = tasks.stream().filter(task -> "OPEN".equals(task.status())).toList();
        assertEquals(1, open.size());
        return open.getFirst();
    }

    private Fixture createFixture() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO customers (id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "CUS-C-" + unique.substring(0, 12)
        );
        jdbcTemplate.update("""
                INSERT INTO customer_profiles (
                    id, customer_id, full_name, identity_reference_ciphertext,
                    identity_reference_fingerprint, identity_reference_last_four,
                    phone_number, residential_address, employment_status, employer_name,
                    terms_consent_accepted, data_processing_consent_accepted
                ) VALUES (?, ?, 'Correction Test Customer', ?, ?, '1234', '0900000000',
                          'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)
                """, UUID.randomUUID(), customerId, "cipher-" + unique, "fingerprint-" + unique);
        jdbcTemplate.update("""
                INSERT INTO customer_bank_accounts (
                    id, customer_id, bank_code, bank_name_snapshot, account_holder_name,
                    account_number_ciphertext, account_number_fingerprint, account_number_last_four,
                    status, primary_account
                ) VALUES (?, ?, 'TEST', 'Test Bank', 'Correction Test Customer', ?, ?, '5678',
                          'ACTIVE', TRUE)
                """, UUID.randomUUID(), customerId, "bank-cipher-" + unique, "bank-fingerprint-" + unique);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, normalized_email, password_hash, user_type, status, display_name, customer_id
                ) VALUES (?, ?, ?, 'not-used', 'CUSTOMER', 'ACTIVE', 'Correction Test Customer', ?)
                """, customerUserId, "correction-" + unique + "@meridian.test",
                "correction-" + unique + "@meridian.test", customerId);
        jdbcTemplate.update("""
                INSERT INTO customer_partner_employee_links (
                    id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id,
                    verification_outcome, link_status, verified_identity_ref, verified_employee_code,
                    last_verified_at, last_refreshed_at
                ) VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', ?, 'MER-EMP-001',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, linkId, customerId, PARTNER_COMPANY_ID, PARTNER_EMPLOYEE_ID, IMPORT_BATCH_ID,
                "test-identity-" + linkId);
        return new Fixture(customerId, customerUserId, linkId);
    }

    private UUID activeCycle(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM loan_application_review_cycles WHERE loan_application_id = ? AND status = 'ACTIVE'",
                UUID.class, applicationId);
    }

    private String cycleStatus(UUID cycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM loan_application_review_cycles WHERE id = ?", String.class, cycleId);
    }

    private int cycleNumber(UUID cycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT cycle_number FROM loan_application_review_cycles WHERE id = ?", Integer.class, cycleId);
    }

    private String status(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?", String.class, applicationId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private void useCustomer() {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(), "correction-customer@meridian.test", "CUSTOMER",
                fixture.customerId(), Set.of("CUSTOMER"),
                Set.of("loan:submit", "loan:read:own", "loan:offer:respond:own", "loan:correction:own")
        ));
    }

    private void useLoanOfficer() {
        currentUserProvider.use(new AuthenticatedUser(
                LOAN_OFFICER_USER_ID, "loan.officer@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:review", "approval:recommend", "document:review")
        ));
    }

    private void useApprover() {
        currentUserProvider.use(new AuthenticatedUser(
                APPROVER_USER_ID, "approver@meridian.local", "STAFF", null,
                Set.of("APPROVER"), Set.of("loan:read", "approval:decide")
        ));
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record Fixture(UUID customerId, UUID customerUserId, UUID linkId) {
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

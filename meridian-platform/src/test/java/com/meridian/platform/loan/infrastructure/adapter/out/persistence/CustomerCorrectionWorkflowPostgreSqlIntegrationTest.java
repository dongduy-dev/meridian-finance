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
import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.CompleteStaffCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitStaffCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

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
    private static final UUID STAFF_WORKER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID BACK_OFFICE_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000305");
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
    @Autowired private QueryStaffCorrectionTasksUseCase staffTaskQuery;
    @Autowired private CompleteStaffCorrectionTaskUseCase staffTaskCompletion;
    @Autowired private ResubmitStaffCorrectionUseCase staffResubmission;
    @Autowired private RespondToApprovedOfferUseCase offerResponseUseCase;
    @Autowired private CancelLoanApplicationUseCase cancellationUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ThreadLocalCurrentUserProvider currentUserProvider;
    @Autowired private Clock clock;
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
        jdbcTemplate.update(
                "UPDATE partner_employee_import_batches SET effective_month = ? WHERE id = ?",
                YearMonth.now(clock).toString(),
                IMPORT_BATCH_ID
        );
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

    @Test
    @Transactional
    void stalePartnerEvidenceRejectsCustomerResubmissionWithoutPartialEffects() {
        SalaryAdvanceApplicationDto application = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), money(3_000_000), 1)
        );
        UUID applicationId = application.loanApplicationId();

        useLoanOfficer();
        reviewStartUseCase.startReview(applicationId);
        UUID cycleId = activeCycle(applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                        null,
                        "Restricted stale-evidence test note.",
                        cycleId,
                        CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                        new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                                CorrectionResponsibility.CUSTOMER,
                                DocumentType.RECENT_PAYSLIP,
                                true,
                                null,
                                null,
                                "Upload a recent payslip.",
                                null
                        )))
                )
        );

        useCustomer();
        CustomerCorrectionTaskDto task = onlyTask(applicationId);
        upload(applicationId, task.checklistItemId(), null, "stale-evidence-payslip.pdf");
        customerTaskCompletion.complete(
                applicationId,
                task.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );

        UUID newerBatchId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO partner_employee_import_batches "
                        + "(id, partner_company_id, effective_month, status, valid_row_count, "
                        + "invalid_row_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'COMPLETED', 0, 0, "
                        + "TIMESTAMP '2099-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                newerBatchId,
                PARTNER_COMPANY_ID,
                YearMonth.now(clock).toString()
        );
        int verificationCount = count(
                "SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId
        );
        int movementCount = count(
                "SELECT count(*) FROM salary_advance_limit_movements WHERE loan_application_id = ?",
                applicationId
        );

        BusinessRuleViolationException failure = assertThrows(
                BusinessRuleViolationException.class,
                () -> customerResubmission.resubmit(
                        applicationId,
                        new CorrectionResubmissionRequest(UUID.randomUUID())
                )
        );

        assertEquals("SALARY_ADVANCE_ELIGIBILITY_DATA_STALE", failure.getErrorCode());
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals("CORRECTION_REQUIRED", cycleStatus(cycleId));
        assertEquals(verificationCount, count(
                "SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals(movementCount, count(
                "SELECT count(*) FROM salary_advance_limit_movements WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals(0, count(
                "SELECT count(*) FROM loan_correction_requests "
                        + "WHERE loan_application_id = ? AND resubmission_request_id IS NOT NULL",
                applicationId
        ));
    }

    @Test
    void staleReturnedCorrectionCanBeCancelledExactlyOnceAndNoLongerBlocksSubmission() {
        ReturnedCorrection returned = createReturnedCustomerCorrection();
        UUID applicationId = returned.applicationId();
        UUID requestId = UUID.randomUUID();
        BigDecimal totalBefore = amount(
                "SELECT total_limit FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        );
        assertEquals(money(3_000_000), amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        int verificationCount = count(
                "SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId
        );
        UUID staleBatchId = makePartnerEvidenceStale();

        CancelLoanApplicationUseCase.Result first = cancellationUseCase.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, applicationId)
        );

        assertEquals("CANCELLED", first.resultingStatus().name());
        assertEquals(false, first.idempotentReplay());
        assertEquals("CANCELLED", status(applicationId));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM loan_correction_requests WHERE loan_application_id = ?",
                String.class,
                applicationId
        ));
        assertEquals(0, customerTaskQuery.findOwnTasks(applicationId).size());
        BusinessStateConflictException taskFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> customerTaskCompletion.complete(
                        applicationId,
                        returned.task().correctionTaskId(),
                        new CompleteCorrectionTaskRequest(UUID.randomUUID())
                )
        );
        assertEquals("CORRECTION_REQUEST_CONFLICT", taskFailure.getErrorCode());
        assertEquals(BigDecimal.ZERO.setScale(2), amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(totalBefore, amount(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_limit_movements "
                        + "WHERE loan_application_id = ? AND movement_type = 'RESERVATION_RELEASED'",
                applicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? "
                        + "AND from_status = 'RETURNED_FOR_REVISION' "
                        + "AND to_status = 'CANCELLED' AND action = 'CANCEL_APPLICATION'",
                applicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_events WHERE entity_id = ? "
                        + "AND action = 'LOAN_APPLICATION_CANCELLED'",
                applicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_events WHERE action = 'RESERVATION_RELEASED' "
                        + "AND payload ->> 'loanApplicationId' = ?",
                applicationId.toString()
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM loan_application_cancellations "
                        + "WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals(verificationCount, count(
                "SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId
        ));

        CancelLoanApplicationUseCase.Result replay = cancellationUseCase.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, applicationId)
        );
        assertEquals(true, replay.idempotentReplay());
        assertEquals(first.cancelledAt(), replay.cancelledAt());
        assertEquals(1, count(
                "SELECT count(*) FROM salary_advance_limit_movements "
                        + "WHERE loan_application_id = ? AND movement_type = 'RESERVATION_RELEASED'",
                applicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? AND action = 'CANCEL_APPLICATION'",
                applicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_events WHERE entity_id = ? "
                        + "AND action = 'LOAN_APPLICATION_CANCELLED'",
                applicationId
        ));
        BusinessStateConflictException newAttempt = assertThrows(
                BusinessStateConflictException.class,
                () -> cancellationUseCase.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), applicationId
                ))
        );
        assertEquals("LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED", newAttempt.getErrorCode());

        jdbcTemplate.update(
                "UPDATE partner_employee_import_batches SET status = 'FAILED' WHERE id = ?",
                staleBatchId
        );
        SalaryAdvanceApplicationDto replacement = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), money(2_000_000), 1)
        );
        assertEquals("SUBMITTED", replacement.status());
        assertEquals(2, count(
                "SELECT count(*) FROM loan_applications WHERE customer_id = ?",
                fixture.customerId()
        ));
    }

    @Test
    void resubmissionThenCancellationRejectsCancellationAndPreservesReservation() {
        ReturnedCorrection returned = createReturnedCustomerCorrection();
        completeCustomerCorrection(returned);

        assertEquals("SUBMITTED", customerResubmission.resubmit(
                returned.applicationId(),
                new CorrectionResubmissionRequest(UUID.randomUUID())
        ).loanApplicationStatus());
        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class,
                () -> cancellationUseCase.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), returned.applicationId()
                ))
        );

        assertEquals("LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED", failure.getErrorCode());
        assertEquals("SUBMITTED", status(returned.applicationId()));
        assertEquals(money(3_000_000), amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(0, count(
                "SELECT count(*) FROM salary_advance_limit_movements "
                        + "WHERE loan_application_id = ? AND movement_type = 'RESERVATION_RELEASED'",
                returned.applicationId()
        ));
    }

    @Test
    void concurrentCancellationAndResubmissionLeaveOneCoherentWinner() throws Exception {
        ReturnedCorrection returned = createReturnedCustomerCorrection();
        completeCustomerCorrection(returned);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CorrectionTerminalRaceOutcome> cancellation = executor.submit(() ->
                    raceCancellation(returned.applicationId(), ready, start));
            Future<CorrectionTerminalRaceOutcome> resubmission = executor.submit(() ->
                    raceCustomerResubmission(returned.applicationId(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<CorrectionTerminalRaceOutcome> outcomes = List.of(
                    cancellation.get(20, TimeUnit.SECONDS),
                    resubmission.get(20, TimeUnit.SECONDS)
            );
            assertEquals(1, outcomes.stream().filter(outcome -> outcome.failure() == null).count());
            assertEquals(1, outcomes.stream().filter(outcome -> outcome.failure() != null).count());
            String finalStatus = status(returned.applicationId());
            if ("CANCELLED".equals(finalStatus)) {
                assertEquals(1, count(
                        "SELECT count(*) FROM salary_advance_limit_movements "
                                + "WHERE loan_application_id = ? "
                                + "AND movement_type = 'RESERVATION_RELEASED'",
                        returned.applicationId()
                ));
                assertEquals(1, count(
                        "SELECT count(*) FROM salary_advance_verifications "
                                + "WHERE loan_application_id = ?",
                        returned.applicationId()
                ));
                assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                        "SELECT status FROM loan_correction_requests WHERE loan_application_id = ?",
                        String.class,
                        returned.applicationId()
                ));
            } else {
                assertEquals("SUBMITTED", finalStatus);
                assertEquals(0, count(
                        "SELECT count(*) FROM salary_advance_limit_movements "
                                + "WHERE loan_application_id = ? "
                                + "AND movement_type = 'RESERVATION_RELEASED'",
                        returned.applicationId()
                ));
                assertEquals(2, count(
                        "SELECT count(*) FROM salary_advance_verifications "
                                + "WHERE loan_application_id = ?",
                        returned.applicationId()
                ));
                assertEquals("RESUBMITTED", jdbcTemplate.queryForObject(
                        "SELECT status FROM loan_correction_requests WHERE loan_application_id = ?",
                        String.class,
                        returned.applicationId()
                ));
            }
        }
    }

    @Test
    void lateCancellationAuditFailureRollsBackEveryTerminalAndFinancialEffect() {
        ReturnedCorrection returned = createReturnedCustomerCorrection();
        BigDecimal reservedBefore = amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        );
        BigDecimal availableBefore = amount(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        );
        int auditBefore = count("SELECT count(*) FROM audit_events");
        doThrow(new IllegalStateException("injected cancellation audit failure"))
                .when(auditPublisher)
                .publish(argThat(event -> event.entries().stream().anyMatch(entry ->
                        entry.action() == BusinessAuditAction.LOAN_APPLICATION_CANCELLED)));

        assertThrows(
                IllegalStateException.class,
                () -> cancellationUseCase.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), returned.applicationId()
                ))
        );

        assertEquals("RETURNED_FOR_REVISION", status(returned.applicationId()));
        assertEquals("OPEN", jdbcTemplate.queryForObject(
                "SELECT status FROM loan_correction_requests WHERE loan_application_id = ?",
                String.class,
                returned.applicationId()
        ));
        assertEquals(reservedBefore, amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(availableBefore, amount(
                "SELECT available_amount FROM salary_advance_limits WHERE customer_id = ?",
                fixture.customerId()
        ));
        assertEquals(0, count(
                "SELECT count(*) FROM salary_advance_limit_movements "
                        + "WHERE loan_application_id = ? AND movement_type = 'RESERVATION_RELEASED'",
                returned.applicationId()
        ));
        assertEquals(0, count(
                "SELECT count(*) FROM loan_application_cancellations WHERE loan_application_id = ?",
                returned.applicationId()
        ));
        assertEquals(0, count(
                "SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? AND action = 'CANCEL_APPLICATION'",
                returned.applicationId()
        ));
        assertEquals(auditBefore, count("SELECT count(*) FROM audit_events"));
    }


    @Test
    void staffAndMixedCorrectionsReturnToReviewAndReachContractPending() throws Exception {
        SalaryAdvanceApplicationDto application = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), money(3_000_000), 1)
        );
        UUID applicationId = application.loanApplicationId();

        useLoanOfficer();
        reviewStartUseCase.startReview(applicationId);
        UUID cycle1 = activeCycle(applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.REQUEST_STAFF_CORRECTION,
                        null,
                        "Restricted staff-correction note.",
                        cycle1,
                        CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                        new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                                CorrectionResponsibility.STAFF,
                                DocumentType.RECENT_PAYSLIP,
                                true,
                                null,
                                null,
                                null,
                                "Upload the requested recent payslip."
                        )))
                )
        );
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals("CORRECTION_REQUIRED", cycleStatus(cycle1));

        useStaffWorker();
        StaffCorrectionTaskDto uploadTask = onlyStaffTask(applicationId);
        useBackOffice();
        DocumentVersionDto version1 = uploadAsStaff(
                applicationId, uploadTask.checklistItemId(), null, "staff-payslip.pdf");

        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                uploadTask.checklistItemId(),
                version1.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted staff-upload acceptance note.",
                LOAN_OFFICER_USER_ID,
                false
        ));

        useStaffWorker();
        UUID staffCompletionId = UUID.randomUUID();
        staffTaskCompletion.complete(
                uploadTask.taskId(),
                new CompleteCorrectionTaskRequest(staffCompletionId)
        );
        staffTaskCompletion.complete(
                uploadTask.taskId(),
                new CompleteCorrectionTaskRequest(staffCompletionId)
        );
        UUID staffResubmissionId = raceStaffResubmissions(applicationId);
        assertEquals("UNDER_REVIEW", staffResubmission.resubmitAsStaff(
                applicationId,
                new CorrectionResubmissionRequest(staffResubmissionId)
        ).loanApplicationStatus());
        assertEquals(2, count(
                "SELECT count(*) FROM salary_advance_verifications WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals("CORRECTED", cycleStatus(cycle1));

        UUID cycle2 = activeCycle(applicationId);
        assertEquals(2, cycleNumber(cycle2));
        useLoanOfficer();
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null)
        );

        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(
                        ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                        null,
                        "Restricted mixed-correction note.",
                        cycle2,
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        new CorrectionPlanRequest(List.of(
                                new CorrectionTaskRequest(
                                        CorrectionScope.DOCUMENT_REPLACEMENT,
                                        CorrectionResponsibility.CUSTOMER,
                                        DocumentType.RECENT_PAYSLIP,
                                        false,
                                        uploadTask.checklistItemId(),
                                        version1.documentVersionId(),
                                        "Upload a clearer replacement payslip.",
                                        null
                                ),
                                new CorrectionTaskRequest(
                                        CorrectionScope.DOCUMENT_REVIEW,
                                        CorrectionResponsibility.STAFF,
                                        DocumentType.RECENT_PAYSLIP,
                                        false,
                                        uploadTask.checklistItemId(),
                                        version1.documentVersionId(),
                                        null,
                                        "Review the replacement payslip."
                                )
                        ))
                )
        );
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals("CORRECTION_REQUIRED", cycleStatus(cycle2));

        useCustomer();
        CustomerCorrectionTaskDto replacementTask = onlyTask(applicationId);
        DocumentVersionDto version2 = raceReplacementAndReview(
                applicationId,
                replacementTask.checklistItemId(),
                version1.documentVersionId()
        );
        customerTaskCompletion.complete(
                applicationId,
                replacementTask.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        AuthorizationException denied = assertThrows(
                AuthorizationException.class,
                () -> customerResubmission.resubmit(
                        applicationId,
                        new CorrectionResubmissionRequest(UUID.randomUUID()))
        );
        assertEquals("CORRECTION_RESUBMISSION_DENIED", denied.getErrorCode());

        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                replacementTask.checklistItemId(),
                version2.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted replacement acceptance note.",
                LOAN_OFFICER_USER_ID,
                false
        ));
        StaffCorrectionTaskDto reviewTask = onlyStaffTask(applicationId);
        staffTaskCompletion.complete(
                reviewTask.taskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        UUID mixedResubmissionId = UUID.randomUUID();
        assertEquals("UNDER_REVIEW", staffResubmission.resubmitAsStaff(
                applicationId,
                new CorrectionResubmissionRequest(mixedResubmissionId)
        ).loanApplicationStatus());

        UUID cycle3 = activeCycle(applicationId);
        assertEquals(3, cycleNumber(cycle3));
        assertEquals("CORRECTED", cycleStatus(cycle2));
        assertEquals(1, count("""
                SELECT count(*)
                FROM approval_decisions decision
                JOIN review_recommendations recommendation
                  ON recommendation.id = decision.review_recommendation_id
                WHERE decision.loan_application_id = ?
                  AND recommendation.review_cycle_id = ?
                  AND decision.decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
                """, applicationId, cycle2));

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
        assertEquals("COMPLETED", cycleStatus(cycle3));
        assertEquals(2, count(
                "SELECT count(*) FROM loan_correction_requests "
                        + "WHERE loan_application_id = ? AND status = 'RESUBMITTED'",
                applicationId
        ));
        assertEquals(0, count("SELECT count(*) FROM audit_events WHERE payload::text LIKE '%Restricted%'"));
    }



    private ReturnedCorrection createReturnedCustomerCorrection() {
        useCustomer();
        SalaryAdvanceApplicationDto application = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), money(3_000_000), 1)
        );
        UUID applicationId = application.loanApplicationId();
        useLoanOfficer();
        reviewStartUseCase.startReview(applicationId);
        UUID cycleId = activeCycle(applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                        null,
                        "Restricted cancellation test note.",
                        cycleId,
                        CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                        new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                                CorrectionResponsibility.CUSTOMER,
                                DocumentType.RECENT_PAYSLIP,
                                true,
                                null,
                                null,
                                "Upload a recent payslip.",
                                null
                        )))
                )
        );
        useCustomer();
        return new ReturnedCorrection(applicationId, cycleId, onlyTask(applicationId));
    }

    private void completeCustomerCorrection(ReturnedCorrection returned) {
        upload(
                returned.applicationId(),
                returned.task().checklistItemId(),
                null,
                "cancellation-race-payslip.pdf"
        );
        customerTaskCompletion.complete(
                returned.applicationId(),
                returned.task().correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
    }

    private UUID makePartnerEvidenceStale() {
        UUID newerBatchId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO partner_employee_import_batches "
                        + "(id, partner_company_id, effective_month, status, valid_row_count, "
                        + "invalid_row_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'COMPLETED', 0, 0, "
                        + "TIMESTAMP '2099-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00')",
                newerBatchId,
                PARTNER_COMPANY_ID,
                YearMonth.now(clock).toString()
        );
        return newerBatchId;
    }

    private CorrectionTerminalRaceOutcome raceCancellation(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        useCustomer();
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Cancellation race did not start.");
        }
        try {
            return new CorrectionTerminalRaceOutcome(
                    cancellationUseCase.cancel(new CancelLoanApplicationUseCase.Command(
                            UUID.randomUUID(), applicationId
                    )).resultingStatus().name(),
                    null
            );
        } catch (RuntimeException exception) {
            return new CorrectionTerminalRaceOutcome(null, exception);
        } finally {
            currentUserProvider.clear();
        }
    }

    private CorrectionTerminalRaceOutcome raceCustomerResubmission(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        useCustomer();
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Resubmission race did not start.");
        }
        try {
            return new CorrectionTerminalRaceOutcome(
                    customerResubmission.resubmit(
                            applicationId,
                            new CorrectionResubmissionRequest(UUID.randomUUID())
                    ).loanApplicationStatus(),
                    null
            );
        } catch (RuntimeException exception) {
            return new CorrectionTerminalRaceOutcome(null, exception);
        } finally {
            currentUserProvider.clear();
        }
    }

    private UUID raceStaffResubmissions(UUID applicationId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StaffResubmissionRaceOutcome> first = executor.submit(() ->
                    raceStaffResubmission(applicationId, firstRequestId, ready, start));
            Future<StaffResubmissionRaceOutcome> second = executor.submit(() ->
                    raceStaffResubmission(applicationId, secondRequestId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<StaffResubmissionRaceOutcome> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            List<StaffResubmissionRaceOutcome> successes = outcomes.stream()
                    .filter(outcome -> outcome.failure() == null)
                    .toList();
            List<RuntimeException> failures = outcomes.stream()
                    .map(StaffResubmissionRaceOutcome::failure)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertEquals(1, successes.size());
            assertEquals("UNDER_REVIEW", successes.getFirst().status());
            assertEquals(1, failures.size());
            assertEquals(
                    "CORRECTION_ALREADY_RESUBMITTED",
                    ((BusinessStateConflictException) failures.getFirst()).getErrorCode()
            );
            return successes.getFirst().requestId();
        }
    }

    private StaffResubmissionRaceOutcome raceStaffResubmission(
            UUID applicationId,
            UUID requestId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        currentUserProvider.use(new AuthenticatedUser(
                STAFF_WORKER_USER_ID, "staff.worker@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:correction:staff")
        ));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Staff resubmission race did not start.");
        }
        try {
            return new StaffResubmissionRaceOutcome(
                    requestId,
                    staffResubmission.resubmitAsStaff(
                            applicationId,
                            new CorrectionResubmissionRequest(requestId)
                    ).loanApplicationStatus(),
                    null
            );
        } catch (RuntimeException exception) {
            return new StaffResubmissionRaceOutcome(requestId, null, exception);
        } finally {
            currentUserProvider.clear();
        }
    }
    private DocumentVersionDto raceReplacementAndReview(
            UUID applicationId,
            UUID checklistItemId,
            UUID baselineVersionId
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<DocumentRaceOutcome> firstUpload = executor.submit(() ->
                    raceCustomerUpload(
                            applicationId, checklistItemId, baselineVersionId,
                            "customer-payslip-v2-a.pdf", ready, start));
            Future<DocumentRaceOutcome> secondUpload = executor.submit(() ->
                    raceCustomerUpload(
                            applicationId, checklistItemId, baselineVersionId,
                            "customer-payslip-v2-b.pdf", ready, start));
            Future<DocumentRaceOutcome> review = executor.submit(() ->
                    raceBaselineReview(
                            applicationId, checklistItemId, baselineVersionId, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<DocumentRaceOutcome> uploads = List.of(
                    firstUpload.get(15, TimeUnit.SECONDS),
                    secondUpload.get(15, TimeUnit.SECONDS)
            );
            DocumentRaceOutcome reviewOutcome = review.get(15, TimeUnit.SECONDS);

            List<DocumentRaceOutcome> successfulUploads = uploads.stream()
                    .filter(outcome -> outcome.version() != null)
                    .toList();
            assertEquals(1, successfulUploads.size());
            List<RuntimeException> uploadFailures = uploads.stream()
                    .map(DocumentRaceOutcome::failure)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertEquals(1, uploadFailures.size());
            assertEquals(
                    "STALE_DOCUMENT_VERSION",
                    ((BusinessStateConflictException) uploadFailures.getFirst()).getErrorCode()
            );
            if (reviewOutcome.failure() != null) {
                assertEquals(
                        "STALE_DOCUMENT_VERSION",
                        ((BusinessStateConflictException) reviewOutcome.failure()).getErrorCode()
                );
            } else {
                assertTrue(reviewOutcome.reviewed());
            }
            return successfulUploads.getFirst().version();
        }
    }

    private DocumentRaceOutcome raceCustomerUpload(
            UUID applicationId,
            UUID checklistItemId,
            UUID baselineVersionId,
            String filename,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(), "correction-customer@meridian.test", "CUSTOMER",
                fixture.customerId(), Set.of("CUSTOMER"),
                Set.of("document:upload:own", "loan:correction:own")
        ));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Document race did not start.");
        }
        try {
            return new DocumentRaceOutcome(
                    upload(applicationId, checklistItemId, baselineVersionId, filename),
                    false,
                    null
            );
        } catch (RuntimeException exception) {
            return new DocumentRaceOutcome(null, false, exception);
        } finally {
            currentUserProvider.clear();
        }
    }

    private DocumentRaceOutcome raceBaselineReview(
            UUID applicationId,
            UUID checklistItemId,
            UUID baselineVersionId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        currentUserProvider.use(new AuthenticatedUser(
                LOAN_OFFICER_USER_ID, "loan.officer@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("document:review")
        ));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Document race did not start.");
        }
        try {
            documentReviewUseCase.review(new ReviewDocumentCommand(
                    applicationId,
                    checklistItemId,
                    baselineVersionId,
                    UUID.randomUUID(),
                    DocumentReviewOutcome.ACCEPT_DOCUMENT,
                    null,
                    "Restricted concurrent baseline review.",
                    LOAN_OFFICER_USER_ID,
                    false
            ));
            return new DocumentRaceOutcome(null, true, null);
        } catch (RuntimeException exception) {
            return new DocumentRaceOutcome(null, false, exception);
        } finally {
            currentUserProvider.clear();
        }
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

    private DocumentVersionDto uploadAsStaff(
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
                DocumentUploaderActorType.STAFF,
                BACK_OFFICE_USER_ID,
                null
        ));
    }

    private CustomerCorrectionTaskDto onlyTask(UUID applicationId) {
        List<CustomerCorrectionTaskDto> tasks = customerTaskQuery.findOwnTasks(applicationId);
        List<CustomerCorrectionTaskDto> open = tasks.stream().filter(task -> "OPEN".equals(task.status())).toList();
        assertEquals(1, open.size());
        return open.getFirst();
    }

    private StaffCorrectionTaskDto onlyStaffTask(UUID applicationId) {
        List<StaffCorrectionTaskDto> open = staffTaskQuery.findStaffTasks(
                        LoanCorrectionTaskStatus.OPEN, 0, 50
                ).stream()
                .filter(task -> task.loanApplicationId().equals(applicationId))
                .toList();
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

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
    }

    private void useCustomer() {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(), "correction-customer@meridian.test", "CUSTOMER",
                fixture.customerId(), Set.of("CUSTOMER"),
                Set.of(
                        "loan:submit",
                        "loan:read:own",
                        "loan:offer:respond:own",
                        "loan:correction:own",
                        "loan:cancel:own"
                )
        ));
    }

    private void useLoanOfficer() {
        currentUserProvider.use(new AuthenticatedUser(
                LOAN_OFFICER_USER_ID, "loan.officer@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of(
                        "loan:review", "approval:recommend", "document:review", "loan:correction:staff")
        ));
    }

    private void useApprover() {
        currentUserProvider.use(new AuthenticatedUser(
                APPROVER_USER_ID, "approver@meridian.local", "STAFF", null,
                Set.of("APPROVER"), Set.of("loan:read", "approval:decide")
        ));
    }

    private void useStaffWorker() {
        currentUserProvider.use(new AuthenticatedUser(
                STAFF_WORKER_USER_ID, "staff.worker@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:correction:staff")
        ));
    }

    private void useBackOffice() {
        currentUserProvider.use(new AuthenticatedUser(
                BACK_OFFICE_USER_ID, "backoffice.admin@meridian.local", "STAFF", null,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("document:upload:staff")
        ));
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record StaffResubmissionRaceOutcome(
            UUID requestId,
            String status,
            RuntimeException failure
    ) {
    }

    private record DocumentRaceOutcome(
            DocumentVersionDto version,
            boolean reviewed,
            RuntimeException failure
    ) {
    }

    private record ReturnedCorrection(
            UUID applicationId,
            UUID reviewCycleId,
            CustomerCorrectionTaskDto task
    ) {
    }

    private record CorrectionTerminalRaceOutcome(
            String status,
            RuntimeException failure
    ) {
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

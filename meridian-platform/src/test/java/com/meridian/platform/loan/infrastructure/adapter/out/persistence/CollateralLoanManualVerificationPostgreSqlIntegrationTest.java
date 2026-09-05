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
import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationVerificationUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
                CollateralLoanManualVerificationPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CollateralLoanManualVerificationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "mer_cl_cp2_"
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
    private static final byte[] PDF = "%PDF-1.7\n% Meridian Collateral evidence\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private StartCollateralLoanApplicationUseCase submissionUseCase;
    @Autowired private UploadDocumentUseCase uploadUseCase;
    @Autowired private ReviewDocumentUseCase documentReviewUseCase;
    @Autowired private ManageCollateralLoanVerificationUseCase verificationUseCase;
    @Autowired private StartLoanApplicationReviewUseCase reviewStartUseCase;
    @Autowired private SubmitReviewRecommendationUseCase recommendationUseCase;
    @Autowired private SubmitApprovalDecisionUseCase decisionUseCase;
    @Autowired private ManageOwnCustomerBankAccountUseCase bankAccountUseCase;
    @Autowired private QueryOwnCorrectionTasksUseCase correctionTaskQuery;
    @Autowired private QueryStaffLoanApplicationVerificationUseCase staffVerificationQuery;
    @Autowired private QueryStaffLoanApplicationReviewUseCase staffReviewQuery;
    @Autowired private CompleteOwnCorrectionTaskUseCase correctionTaskCompletion;
    @Autowired private ResubmitOwnCorrectionUseCase correctionResubmission;
    @Autowired private JdbcTemplate jdbc;
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
        bankAccountUseCase.addBankAccount(new AddCustomerBankAccountRequest(
                "TEST", "Test Bank", "Collateral CP2 Customer", "12345678901234"
        ));
    }

    @AfterEach
    void clearUser() {
        currentUserProvider.clear();
    }

    @ParameterizedTest
    @EnumSource(ApprovalDecisionAction.class)
    void verifiedCollateralExecutesEveryApprovalAction(ApprovalDecisionAction action) {
        ReadyApplication ready = originateAndMakeProcessingReady();
        useLoanOfficer();
        CollateralLoanVerificationStartDto started = verificationUseCase.startManualVerification(
                ready.applicationId()
        );
        verificationUseCase.completeManualVerification(
                ready.applicationId(),
                completion(started.verificationId(), CollateralLoanManualVerificationOutcome.VERIFIED,
                        "Ownership evidence and submitted Collateral facts were assessed.")
        );
        assertEquals("SUBMITTED", status(ready.applicationId()));

        assertEquals("UNDER_REVIEW", reviewStartUseCase.startReview(ready.applicationId()).status());
        var reviewRead = staffReviewQuery.query(ready.applicationId());
        assertEquals("ACTIVE", reviewRead.currentReviewCycle().status());
        assertEquals("UNDER_REVIEW", reviewRead.applicationStatus());
        assertTrue(!reviewRead.reviewStartAvailable());
        recommendationUseCase.submitReviewRecommendation(
                ready.applicationId(),
                new ReviewRecommendationRequest(ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null)
        );
        assertEquals("APPROVAL_PENDING", status(ready.applicationId()));
        UUID reviewCycleId = uuid("SELECT id FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", ready.applicationId());
        useApprover();
        decisionUseCase.submitApprovalDecision(
                ready.applicationId(),
                approvalRequest(action, reviewCycleId, ready)
        );

        String expectedStatus = switch (action) {
            case APPROVE -> "CUSTOMER_ACCEPTANCE_PENDING";
            case REJECT -> "REJECTED";
            case RETURN_TO_LOAN_OFFICER_REVIEW -> "RETURNED_TO_REVIEW";
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> "RETURNED_FOR_REVISION";
        };
        assertEquals(expectedStatus, status(ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?",
                ready.applicationId()));
        assertEquals(action == ApprovalDecisionAction.APPROVE ? 1 : 0,
                count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?",
                        ready.applicationId()));
        String expectedCycleStatus = switch (action) {
            case APPROVE, REJECT -> "COMPLETED";
            case RETURN_TO_LOAN_OFFICER_REVIEW -> "SUPERSEDED";
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> "CORRECTION_REQUIRED";
        };
        assertEquals(expectedCycleStatus, text("SELECT status FROM loan_application_review_cycles "
                + "WHERE id = ?", reviewCycleId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", ready.applicationId()));
    }

    @Test
    void concurrentVerificationStartsProduceOneStartAndNoReplay() throws Exception {
        ReadyApplication ready = originateAndMakeProcessingReady();
        List<CommandOutcome> outcomes = runConcurrently(
                () -> startVerification(ready.applicationId(), LOAN_OFFICER_USER_ID),
                () -> startVerification(ready.applicationId(), SECOND_STAFF_USER_ID)
        );

        assertEquals(1, successful(outcomes));
        BusinessStateConflictException failure = assertInstanceOf(
                BusinessStateConflictException.class,
                failed(outcomes)
        );
        assertEquals("PRODUCT_VERIFICATION_START_NOT_ALLOWED", failure.getErrorCode());
        assertEquals("VERIFICATION_PENDING", status(ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM collateral_loan_verifications "
                + "WHERE loan_application_id = ?", ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'START_PRODUCT_VERIFICATION'",
                ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'COLLATERAL_LOAN_VERIFICATION_STARTED'", ready.applicationId()));
    }

    @Test
    void concurrentVerificationCompletionsProduceOneImmutableDecision() throws Exception {
        ReadyApplication ready = originateAndMakeProcessingReady();
        useLoanOfficer();
        UUID verificationId = verificationUseCase.startManualVerification(ready.applicationId()).verificationId();
        List<CommandOutcome> outcomes = runConcurrently(
                () -> completeVerified(ready.applicationId(), verificationId,
                        LOAN_OFFICER_USER_ID, "First concurrent assessment."),
                () -> completeVerified(ready.applicationId(), verificationId,
                        SECOND_STAFF_USER_ID, "Second concurrent assessment.")
        );

        assertEquals(1, successful(outcomes));
        assertInstanceOf(BusinessStateConflictException.class, failed(outcomes));
        assertEquals("SUBMITTED", status(ready.applicationId()));
        assertEquals("VERIFIED", text("SELECT product_verification_result "
                + "FROM collateral_loan_verifications WHERE id = ?", verificationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'",
                ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'COLLATERAL_LOAN_VERIFICATION_COMPLETED'", ready.applicationId()));
    }

    @Test
    void completionAndReviewRaceNeverStartsReviewBeforeVerifiedEvidence() throws Exception {
        ReadyApplication ready = originateAndMakeProcessingReady();
        useLoanOfficer();
        UUID verificationId = verificationUseCase.startManualVerification(ready.applicationId()).verificationId();
        List<CommandOutcome> outcomes = runConcurrently(
                () -> completeVerified(ready.applicationId(), verificationId,
                        LOAN_OFFICER_USER_ID, "Concurrent completion assessment."),
                () -> startReview(ready.applicationId(), SECOND_STAFF_USER_ID)
        );

        assertTrue(outcomes.getFirst().successful());
        assertEquals("VERIFIED", text("SELECT product_verification_result "
                + "FROM collateral_loan_verifications WHERE id = ?", verificationId));
        if (outcomes.get(1).successful()) {
            assertEquals("UNDER_REVIEW", status(ready.applicationId()));
            assertEquals(1, count("SELECT count(*) FROM loan_application_review_cycles "
                    + "WHERE loan_application_id = ?", ready.applicationId()));
        } else {
            BusinessRuleViolationException failure = assertInstanceOf(
                    BusinessRuleViolationException.class,
                    outcomes.get(1).failure()
            );
            assertEquals("PRODUCT_VERIFICATION_PENDING", failure.getErrorCode());
            assertEquals("SUBMITTED", status(ready.applicationId()));
            assertEquals(0, count("SELECT count(*) FROM loan_application_review_cycles "
                    + "WHERE loan_application_id = ?", ready.applicationId()));
        }
    }

    @Test
    void mandatoryCompletionAuditFailureRollsBackVerificationStatusHistoryAndAudit() {
        ReadyApplication ready = originateAndMakeProcessingReady();
        useLoanOfficer();
        UUID verificationId = verificationUseCase.startManualVerification(ready.applicationId()).verificationId();
        int auditBefore = count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'COLLATERAL_LOAN_VERIFICATION_COMPLETED'", ready.applicationId());
        doThrow(new IllegalStateException("simulated mandatory Collateral completion audit failure"))
                .when(auditPublisher)
                .publish(argThat(this::containsCompletionAudit));

        assertThrows(IllegalStateException.class, () -> verificationUseCase.completeManualVerification(
                ready.applicationId(),
                completion(verificationId, CollateralLoanManualVerificationOutcome.VERIFIED,
                        "This completion must roll back.")
        ));

        assertEquals("VERIFICATION_PENDING", status(ready.applicationId()));
        assertEquals("PENDING_MANUAL_REVIEW", text("SELECT product_verification_result "
                + "FROM collateral_loan_verifications WHERE id = ?", verificationId));
        assertNull(nullableUuid("SELECT reviewed_by_user_id FROM collateral_loan_verifications "
                + "WHERE id = ?", verificationId));
        assertNull(text("SELECT assessment_note FROM collateral_loan_verifications WHERE id = ?",
                verificationId));
        assertEquals(0, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'",
                ready.applicationId()));
        assertEquals(auditBefore, count("SELECT count(*) FROM audit_events WHERE entity_id = ? "
                + "AND action = 'COLLATERAL_LOAN_VERIFICATION_COMPLETED'", ready.applicationId()));
    }

    @Test
    void concurrentMoreInformationCreatesOneCorrectionAndConcurrentResubmissionOneNextCycle()
            throws Exception {
        ReadyApplication ready = originateAndMakeProcessingReady();
        useLoanOfficer();
        UUID firstVerificationId = verificationUseCase.startManualVerification(
                ready.applicationId()
        ).verificationId();
        CompleteCollateralLoanVerificationRequest request = moreInformation(
                firstVerificationId,
                ready,
                "Ownership evidence must be replaced and reassessed."
        );
        List<CommandOutcome> completions = runConcurrently(
                () -> complete(ready.applicationId(), request, LOAN_OFFICER_USER_ID),
                () -> complete(ready.applicationId(), request, SECOND_STAFF_USER_ID)
        );

        assertEquals(1, successful(completions));
        assertEquals("RETURNED_FOR_REVISION", status(ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", ready.applicationId()));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ?", ready.applicationId()));

        completeReplacement(ready);
        List<CommandOutcome> resubmissions = runConcurrently(
                () -> resubmit(ready.applicationId(), UUID.randomUUID()),
                () -> resubmit(ready.applicationId(), UUID.randomUUID())
        );

        assertEquals(1, successful(resubmissions));
        assertEquals("SUBMITTED", status(ready.applicationId()));
        assertEquals(2, count("SELECT count(*) FROM collateral_loan_verifications "
                + "WHERE loan_application_id = ?", ready.applicationId()));
        UUID correctionId = uuid("SELECT id FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", ready.applicationId());
        UUID latestVerificationId = uuid("SELECT id FROM collateral_loan_verifications "
                + "WHERE loan_application_id = ? ORDER BY verification_sequence DESC LIMIT 1",
                ready.applicationId());
        assertEquals(correctionId, uuid("SELECT source_correction_request_id "
                + "FROM collateral_loan_verifications WHERE id = ?", latestVerificationId));
        assertEquals("REQUIRES_MORE_INFORMATION", text("SELECT product_verification_result "
                + "FROM collateral_loan_verifications WHERE id = ?", firstVerificationId));

        useLoanOfficer();
        var verificationRead = staffVerificationQuery.query(ready.applicationId());
        var productRead = (com.meridian.platform.loan.application.dto
                .StaffLoanApplicationVerificationDto.ManualVerificationDto)
                verificationRead.productVerification();
        assertEquals(List.of(1, 2), productRead.history().stream()
                .map(item -> item.verificationSequence()).toList());
        assertEquals(latestVerificationId, productRead.currentCycle().verificationId());
        assertEquals("CAR", productRead.collateral().collateralType());
        assertEquals(ready.checklistItemId(), verificationRead.correctionTargets()
                .getFirst().checklistItemId());

        BusinessStateConflictException stale = assertThrows(
                BusinessStateConflictException.class,
                () -> verificationUseCase.completeManualVerification(
                        ready.applicationId(),
                        completion(firstVerificationId, CollateralLoanManualVerificationOutcome.VERIFIED,
                                "Delayed completion for the old cycle.")
                )
        );
        assertEquals("STALE_COLLATERAL_VERIFICATION", stale.getErrorCode());
        assertEquals("PENDING_MANUAL_REVIEW", text("SELECT product_verification_result "
                + "FROM collateral_loan_verifications WHERE id = ?", latestVerificationId));
    }

    private ReadyApplication originateAndMakeProcessingReady() {
        useCustomer();
        CollateralLoanApplicationDto application = submissionUseCase.startCollateralLoanApplication(
                new CollateralLoanApplicationRequest(
                        new BigDecimal("25000000"),
                        12,
                        new CollateralDetailsRequest(
                                CollateralType.CAR,
                                "Customer vehicle",
                                new BigDecimal("50000000"),
                                "Customer-submitted ownership statement",
                                "Normal used condition"
                        )
                )
        );
        UUID checklistItemId = application.evidenceRequirements().getFirst().checklistItemId();
        DocumentVersionDto version = upload(
                application.loanApplicationId(), checklistItemId, null, "ownership-evidence.pdf"
        );
        useLoanOfficer();
        acceptDocument(application.loanApplicationId(), checklistItemId, version.documentVersionId());
        assertEquals("SUBMITTED", status(application.loanApplicationId()));
        return new ReadyApplication(application.loanApplicationId(), checklistItemId, version.documentVersionId());
    }

    private void completeReplacement(ReadyApplication ready) {
        useCustomer();
        List<CustomerCorrectionTaskDto> tasks = correctionTaskQuery.findOwnTasks(ready.applicationId());
        assertEquals(1, tasks.size());
        CustomerCorrectionTaskDto task = tasks.getFirst();
        DocumentVersionDto replacement = upload(
                ready.applicationId(),
                task.checklistItemId(),
                ready.documentVersionId(),
                "replacement-ownership-evidence.pdf"
        );
        correctionTaskCompletion.complete(
                ready.applicationId(),
                task.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        useLoanOfficer();
        acceptDocument(ready.applicationId(), task.checklistItemId(), replacement.documentVersionId());
    }

    private DocumentVersionDto upload(
            UUID applicationId,
            UUID checklistItemId,
            UUID replacesVersionId,
            String filename
    ) {
        return uploadUseCase.upload(new UploadDocumentCommand(
                applicationId,
                checklistItemId,
                UUID.randomUUID(),
                replacesVersionId,
                filename,
                "application/pdf",
                new ByteArrayInputStream(PDF),
                DocumentUploaderActorType.CUSTOMER,
                fixture.customerUserId(),
                fixture.customerId()
        ));
    }

    private void acceptDocument(UUID applicationId, UUID checklistItemId, UUID documentVersionId) {
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                checklistItemId,
                documentVersionId,
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted Collateral ownership evidence review.",
                LOAN_OFFICER_USER_ID,
                false
        ));
    }

    private CompleteCollateralLoanVerificationRequest completion(
            UUID verificationId,
            CollateralLoanManualVerificationOutcome outcome,
            String note
    ) {
        return new CompleteCollateralLoanVerificationRequest(
                verificationId, outcome, note, null, null
        );
    }

    private CompleteCollateralLoanVerificationRequest moreInformation(
            UUID verificationId,
            ReadyApplication ready,
            String note
    ) {
        return new CompleteCollateralLoanVerificationRequest(
                verificationId,
                CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                note,
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(ready)
        );
    }

    private CorrectionPlanRequest replacementPlan(ReadyApplication ready) {
        return new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                false,
                ready.checklistItemId(),
                ready.documentVersionId(),
                "Replace the existing ownership evidence with a clear current version.",
                null
        )));
    }

    private CorrectionPlanRequest mixedCorrectionPlan(ReadyApplication ready) {
        return new CorrectionPlanRequest(List.of(
                replacementPlan(ready).tasks().getFirst(),
                new CorrectionTaskRequest(
                        CorrectionScope.DOCUMENT_REVIEW,
                        CorrectionResponsibility.STAFF,
                        DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                        false,
                        ready.checklistItemId(),
                        ready.documentVersionId(),
                        null,
                        "Review the replacement ownership evidence before resubmission."
                )
        ));
    }

    private ApprovalDecisionRequest approvalRequest(
            ApprovalDecisionAction action,
            UUID reviewCycleId,
            ReadyApplication ready
    ) {
        return switch (action) {
            case APPROVE -> new ApprovalDecisionRequest(action, null, null);
            case REJECT -> new ApprovalDecisionRequest(action, "Not approved.", null);
            case RETURN_TO_LOAN_OFFICER_REVIEW -> new ApprovalDecisionRequest(
                        ApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW,
                        "Return for further Loan Officer review.",
                        null
                );
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> new ApprovalDecisionRequest(
                        ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                        null,
                        null,
                        reviewCycleId,
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        mixedCorrectionPlan(ready)
                );
        };
    }

    private void startVerification(UUID applicationId, UUID actorId) {
        useStaff(actorId);
        verificationUseCase.startManualVerification(applicationId);
    }

    private void completeVerified(UUID applicationId, UUID verificationId, UUID actorId, String note) {
        complete(applicationId, completion(
                verificationId, CollateralLoanManualVerificationOutcome.VERIFIED, note
        ), actorId);
    }

    private void complete(
            UUID applicationId,
            CompleteCollateralLoanVerificationRequest request,
            UUID actorId
    ) {
        useStaff(actorId);
        verificationUseCase.completeManualVerification(applicationId, request);
    }

    private void startReview(UUID applicationId, UUID actorId) {
        useStaff(actorId);
        reviewStartUseCase.startReview(applicationId);
    }

    private void resubmit(UUID applicationId, UUID requestId) {
        useCustomer();
        correctionResubmission.resubmit(applicationId, new CorrectionResubmissionRequest(requestId));
    }

    private List<CommandOutcome> runConcurrently(Command first, Command second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> firstFuture = executor.submit(() -> afterBarrier(first, ready, start));
            Future<CommandOutcome> secondFuture = executor.submit(() -> afterBarrier(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS)
            );
        }
    }

    private CommandOutcome afterBarrier(Command command, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent Collateral command did not start.");
            }
            command.execute();
            return CommandOutcome.success();
        } catch (RuntimeException exception) {
            return CommandOutcome.failure(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CommandOutcome.failure(new IllegalStateException(
                    "Concurrent Collateral command was interrupted.", exception
            ));
        } finally {
            currentUserProvider.clear();
        }
    }

    private long successful(List<CommandOutcome> outcomes) {
        return outcomes.stream().filter(CommandOutcome::successful).count();
    }

    private RuntimeException failed(List<CommandOutcome> outcomes) {
        return outcomes.stream()
                .filter(outcome -> !outcome.successful())
                .map(CommandOutcome::failure)
                .findFirst()
                .orElseThrow();
    }

    private boolean containsCompletionAudit(BusinessAuditEvent event) {
        return event != null && event.entries().stream().anyMatch(
                entry -> entry.action() == BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_COMPLETED
        );
    }

    private Fixture createReadyCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");
        jdbc.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "CL-CP2-" + unique.substring(0, 12));
        jdbc.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'Collateral CP2 Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), customerId, "identity-" + unique);
        jdbc.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', "
                        + "'Collateral CP2 Customer', ?)",
                customerUserId,
                "cl-cp2-" + unique + "@meridian.test",
                "cl-cp2-" + unique + "@meridian.test",
                customerId);
        return new Fixture(customerId, customerUserId);
    }

    private void useCustomer() {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(),
                "collateral-cp2-customer@meridian.test",
                "CUSTOMER",
                fixture.customerId(),
                Set.of("CUSTOMER"),
                Set.of("loan:submit", "document:upload:own", "loan:read:own", "loan:correction:own")
        ));
    }

    private void useLoanOfficer() {
        useStaff(LOAN_OFFICER_USER_ID);
    }

    private void useStaff(UUID userId) {
        currentUserProvider.use(new AuthenticatedUser(
                userId,
                "collateral-cp2-staff@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review", "approval:recommend", "document:review")
        ));
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

    private String status(UUID applicationId) {
        return text("SELECT status FROM loan_applications WHERE id = ?", applicationId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private UUID uuid(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private UUID nullableUuid(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private record Fixture(UUID customerId, UUID customerUserId) {
    }

    private record ReadyApplication(
            UUID applicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
    }

    private record CommandOutcome(boolean successful, RuntimeException failure) {
        static CommandOutcome success() {
            return new CommandOutcome(true, null);
        }

        static CommandOutcome failure(RuntimeException failure) {
            return new CommandOutcome(false, failure);
        }
    }

    @FunctionalInterface
    private interface Command {
        void execute();
    }

    static class ThreadLocalCurrentUserProvider implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> currentUser = new ThreadLocal<>();

        void use(AuthenticatedUser authenticatedUser) {
            currentUser.set(authenticatedUser);
        }

        void clear() {
            currentUser.remove();
        }

        @Override
        public AuthenticatedUser currentUser() {
            return currentUser.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCurrentUserConfiguration {
        @Bean
        @Primary
        ThreadLocalCurrentUserProvider threadLocalCurrentUserProvider() {
            return new ThreadLocalCurrentUserProvider();
        }
    }
}

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
import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.CompleteStaffCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitStaffCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ContractSupersessionReason;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanManualVerificationOutcome;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final byte[] PDF = "%PDF-1.7\n% Meridian UCL evidence\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private StartUnsecuredConsumerLoanApplicationUseCase submissionUseCase;
    @Autowired private UploadDocumentUseCase uploadUseCase;
    @Autowired private ReviewDocumentUseCase documentReviewUseCase;
    @Autowired private ManageUnsecuredConsumerLoanVerificationUseCase verificationUseCase;
    @Autowired private StartLoanApplicationReviewUseCase reviewStartUseCase;
    @Autowired private SubmitReviewRecommendationUseCase recommendationUseCase;
    @Autowired private SubmitApprovalDecisionUseCase decisionUseCase;
    @Autowired private QueryApprovedOfferUseCase queryApprovedOfferUseCase;
    @Autowired private RespondToApprovedOfferUseCase respondToApprovedOfferUseCase;
    @Autowired private ManageOwnCustomerBankAccountUseCase bankAccountUseCase;
    @Autowired private PrepareLoanContractUseCase prepareLoanContractUseCase;
    @Autowired private AcknowledgeLoanContractUseCase acknowledgeLoanContractUseCase;
    @Autowired private QueryContractReadinessUseCase queryContractReadinessUseCase;
    @Autowired private ConfirmContractReadinessUseCase confirmContractReadinessUseCase;
    @Autowired private ConfirmManualDisbursementUseCase confirmManualDisbursementUseCase;
    @Autowired private RecordRepaymentUseCase recordRepaymentUseCase;
    @Autowired private CloseLoanAccountUseCase closeLoanAccountUseCase;
    @Autowired private QueryOwnCorrectionTasksUseCase correctionTaskQuery;
    @Autowired private CompleteOwnCorrectionTaskUseCase correctionTaskCompletion;
    @Autowired private ResubmitOwnCorrectionUseCase correctionResubmission;
    @Autowired private CompleteStaffCorrectionTaskUseCase staffTaskCompletion;
    @Autowired private ResubmitStaffCorrectionUseCase staffCorrectionResubmission;
    @Autowired private CancelLoanApplicationUseCase cancellationUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ThreadLocalCurrentUserProvider currentUserProvider;
    @MockitoSpyBean private BusinessAuditPublisher auditPublisher;
    @MockitoSpyBean private SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    @MockitoSpyBean private SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;

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
        reset(auditPublisher, salaryAdvanceVerificationRepository, salaryAdvanceLimitMovementRepository);
        fixture = createReadyCustomer();
        useCustomer();
        bankAccountUseCase.addBankAccount(new AddCustomerBankAccountRequest(
                "TEST", "Test Bank", "UCL Customer", "12345678905678"
        ));
    }

    @AfterEach
    void clearUser() {
        currentUserProvider.clear();
    }

    @Test
    void documentBackedUclLifecycleReachesDisbursedWithoutSalaryExposure() {
        UUID applicationId = originateToApprovalPending();
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?", applicationId));
        assertEquals(6, count("SELECT count(*) FROM approved_offer_repayment_items item "
                + "JOIN approved_offers offer ON offer.id = item.approved_offer_id "
                + "WHERE offer.loan_application_id = ?", applicationId));
        assertEquals("COMPLETED", text("SELECT status FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'APPROVE'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'GENERATE_APPROVED_OFFER'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'APPROVAL_DECISION_RECORDED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));
        assertEquals(1, count("SELECT count(*) FROM audit_events WHERE action = 'APPROVED_OFFER_GENERATED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));

        useCustomer();
        int transitionCountBeforeRead = count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ?", applicationId);
        ApprovedOfferDto offer = queryApprovedOfferUseCase.getApprovedOffer(applicationId);
        assertEquals("PENDING", offer.status());
        assertEquals(new BigDecimal("5000000.00"), offer.approvedPrincipal());
        assertEquals(6, offer.approvedTermMonths());
        assertEquals("FLAT_ORIGINAL_PRINCIPAL", offer.interestCalculationMethod());
        assertEquals(new BigDecimal("0.018000"), offer.flatMonthlyInterestRate());
        assertEquals(new BigDecimal("540000.00"), offer.totalInterest());
        assertEquals(new BigDecimal("0.00"), offer.feeAmount());
        assertEquals(new BigDecimal("5540000.00"), offer.totalRepaymentAmount());
        assertEquals("MONTHLY_INSTALLMENT", offer.repaymentMethod());
        assertEquals(6, offer.repaymentItems().size());
        assertEquals(offer.generatedAt().plusDays(7), offer.expiresAt());
        assertEquals(transitionCountBeforeRead, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals("PENDING", text("SELECT status FROM approved_offers WHERE loan_application_id = ?", applicationId));

        ApprovedOfferActionResult accepted = respondToApprovedOfferUseCase.acceptOffer(applicationId);
        assertEquals("ACCEPTED", accepted.offer().status());
        assertEquals(offer.approvedPrincipal(), accepted.offer().approvedPrincipal());
        assertEquals(offer.totalInterest(), accepted.offer().totalInterest());
        assertEquals(offer.totalRepaymentAmount(), accepted.offer().totalRepaymentAmount());
        assertEquals(offer.repaymentItems(), accepted.offer().repaymentItems());
        assertEquals("CONTRACT_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers "
                + "WHERE loan_application_id = ? AND status = 'ACCEPTED'", applicationId));

        useAccounting();
        LoanContract prepared = prepareLoanContractUseCase.prepare(
                new PrepareLoanContractUseCase.Command(UUID.randomUUID(), applicationId, 0, null)
        );
        assertEquals(LoanContractStatus.PREPARED, prepared.status());
        assertEquals(offer.approvedPrincipal(), prepared.financialTerms().approvedPrincipal());
        assertEquals(offer.approvedTermMonths(), prepared.financialTerms().approvedTermMonths());
        assertEquals(offer.flatMonthlyInterestRate(),
                prepared.financialTerms().flatMonthlyInterestRate());
        assertEquals(offer.totalInterest(), prepared.financialTerms().totalInterest());
        assertEquals(offer.feeAmount(), prepared.financialTerms().feeAmount());
        assertEquals(offer.totalRepaymentAmount(),
                prepared.financialTerms().totalRepaymentAmount());
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT,
                prepared.financialTerms().repaymentMethod());
        assertEquals(offer.repaymentItems().size(), prepared.repaymentItems().size());
        for (int index = 0; index < offer.repaymentItems().size(); index++) {
            var offerItem = offer.repaymentItems().get(index);
            var contractItem = prepared.repaymentItems().get(index);
            assertEquals(offerItem.installmentNumber(), contractItem.installmentNumber());
            assertEquals(offerItem.principalDue(), contractItem.principalDue());
            assertEquals(offerItem.interestDue(), contractItem.interestDue());
            assertEquals(offerItem.feeDue(), contractItem.feeDue());
            assertEquals(offerItem.totalDue(), contractItem.totalDue());
        }
        assertTrue(count("SELECT octet_length(protected_account_number) FROM loan_contracts "
                + "WHERE id = ?", prepared.id()) > 0);

        useCustomer();
        LoanContract acknowledged = acknowledgeLoanContractUseCase.acknowledge(
                new AcknowledgeLoanContractUseCase.Command(
                        UUID.randomUUID(), applicationId, prepared.contractVersion()
                )
        );
        assertEquals(LoanContractStatus.ACKNOWLEDGED, acknowledged.status());

        useAccounting();
        QueryContractReadinessUseCase.Snapshot readiness =
                queryContractReadinessUseCase.query(applicationId, prepared.contractVersion());
        assertTrue(readiness.ready());
        assertTrue(readiness.blockers().isEmpty());
        LoanContract ready = confirmContractReadinessUseCase.confirm(
                new ConfirmContractReadinessUseCase.Command(
                        UUID.randomUUID(), applicationId, prepared.id(), prepared.contractVersion()
                )
        );
        assertEquals(LoanContractStatus.READY_FOR_DISBURSEMENT, ready.status());
        assertEquals("DISBURSEMENT_PENDING", status(applicationId));

        LocalDate valueDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstRepaymentDate = valueDate.plusMonths(1);
        UUID disbursementRequestId = UUID.randomUUID();
        ConfirmManualDisbursementUseCase.Command disbursementCommand =
                new ConfirmManualDisbursementUseCase.Command(
                        disbursementRequestId,
                        applicationId,
                        ready.contractVersion(),
                        "UCL-TRANSFER-" + applicationId,
                        valueDate,
                        firstRepaymentDate
                );
        ConfirmManualDisbursementUseCase.Result disbursed =
                confirmManualDisbursementUseCase.confirm(disbursementCommand);

        assertEquals("DISBURSED", status(applicationId));
        assertEquals("ACTIVE", disbursed.loanAccountStatus().name());
        assertEquals(6, disbursed.scheduleItems().size());
        assertEquals(firstRepaymentDate, disbursed.scheduleItems().getFirst().dueDate());
        for (int index = 0; index < disbursed.scheduleItems().size(); index++) {
            var finalItem = disbursed.scheduleItems().get(index);
            var contractItem = prepared.repaymentItems().get(index);
            assertEquals(anchoredDate(firstRepaymentDate, index), finalItem.dueDate());
            assertEquals(contractItem.id(), finalItem.sourceLoanContractRepaymentItemId());
            assertEquals(contractItem.principalDue(), finalItem.principalDue());
            assertEquals(contractItem.interestDue(), finalItem.interestDue());
            assertEquals(contractItem.feeDue(), finalItem.feeDue());
            assertEquals(contractItem.totalDue(), finalItem.totalDue());
        }
        assertEquals(1, count("SELECT count(*) FROM loan_accounts WHERE loan_application_id = ?",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM manual_disbursements "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM repayment_schedules "
                + "WHERE loan_application_id = ? AND schedule_type = 'FINAL'", applicationId));
        assertEquals(6, count("SELECT count(*) FROM repayment_schedule_items item "
                + "JOIN repayment_schedules schedule ON schedule.id = item.repayment_schedule_id "
                + "WHERE schedule.loan_application_id = ?", applicationId));
        assertEquals(6, count("SELECT count(*) FROM repayment_installment_progress progress "
                + "JOIN repayment_schedule_items item ON item.id = progress.repayment_schedule_item_id "
                + "JOIN repayment_schedules schedule ON schedule.id = item.repayment_schedule_id "
                + "WHERE schedule.loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_account_status_transitions history "
                + "JOIN loan_accounts account ON account.id = history.loan_account_id "
                + "WHERE account.loan_application_id = ? AND history.action = 'ACTIVATION_INITIALIZED'",
                applicationId));
        assertEquals(6, count("SELECT count(*) FROM repayment_installment_status_transitions history "
                + "JOIN repayment_schedule_items item ON item.id = history.repayment_schedule_item_id "
                + "JOIN repayment_schedules schedule ON schedule.id = item.repayment_schedule_id "
                + "WHERE schedule.loan_application_id = ? AND history.action = 'ACTIVATION_INITIALIZED'",
                applicationId));
        assertEquals(new BigDecimal("0.00"), jdbcTemplate.queryForObject(
                "SELECT total_paid FROM loan_accounts WHERE loan_application_id = ?",
                BigDecimal.class, applicationId
        ));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'MANUAL_DISBURSEMENT_CONFIRMED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals("VERIFIED", text("SELECT product_verification_result "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM review_recommendations WHERE loan_application_id = ?",
                applicationId));

        ConfirmManualDisbursementUseCase.Result replay =
                confirmManualDisbursementUseCase.confirm(disbursementCommand);
        assertTrue(replay.idempotentReplay());
        assertEquals(disbursed.loanAccountId(), replay.loanAccountId());
        assertEquals(disbursed.manualDisbursementId(), replay.manualDisbursementId());
        assertEquals(disbursed.repaymentScheduleId(), replay.repaymentScheduleId());
        assertEquals(1, count("SELECT count(*) FROM loan_accounts WHERE loan_application_id = ?",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'MANUAL_DISBURSEMENT_CONFIRMED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));
        BusinessStateConflictException contradictoryReplay = assertThrows(
                BusinessStateConflictException.class,
                () -> confirmManualDisbursementUseCase.confirm(
                        new ConfirmManualDisbursementUseCase.Command(
                                disbursementRequestId,
                                applicationId,
                                ready.contractVersion(),
                                "UCL-DIFFERENT-TRANSFER-" + applicationId,
                                valueDate,
                                firstRepaymentDate
                        )
                )
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", contradictoryReplay.getErrorCode());

        useAccounting();
        RecordRepaymentUseCase.Result payoff = recordRepaymentUseCase.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), applicationId,
                        "UCL-FULL-LIFECYCLE-PAYOFF-" + applicationId,
                        offer.totalRepaymentAmount(), valueDate
                )
        );
        assertEquals("SETTLED", payoff.accountBalance().status().name());
        assertEquals(0, payoff.principalReleased().compareTo(BigDecimal.ZERO));
        CloseLoanAccountUseCase.Result closed = closeLoanAccountUseCase.close(
                new CloseLoanAccountUseCase.Command(UUID.randomUUID(), applicationId)
        );
        assertEquals("CLOSED", closed.resultingStatus().name());
        assertEquals("DISBURSED", status(applicationId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
        verifyNoInteractions(salaryAdvanceVerificationRepository, salaryAdvanceLimitMovementRepository);
    }

    @Test
    void uclCustomerDeclineCreatesNoSalaryReservationRelease() {
        UUID applicationId = originateToApprovalPending();
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );

        useCustomer();
        ApprovedOfferActionResult declined = respondToApprovedOfferUseCase.declineOffer(applicationId);

        assertEquals("DECLINED", declined.offer().status());
        assertEquals("CUSTOMER_DECLINED", status(applicationId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
        verifyNoInteractions(salaryAdvanceVerificationRepository, salaryAdvanceLimitMovementRepository);
    }

    @Test
    void uclDestinationRefreshSupersedesContractWithoutChangingFinancialTerms() {
        AcceptedUcl accepted = acceptedUcl();
        useAccounting();
        LoanContract first = prepareLoanContractUseCase.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), accepted.applicationId, 0, null
                )
        );

        useCustomer();
        var refreshedAccount = bankAccountUseCase.addBankAccount(
                new AddCustomerBankAccountRequest(
                        "ALT", "Alternate Test Bank", "UCL Customer", "99887766554433"
                )
        );
        bankAccountUseCase.makePrimary(refreshedAccount.customerBankAccountId());

        useAccounting();
        LoanContract second = prepareLoanContractUseCase.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), accepted.applicationId, 1,
                        ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH
                )
        );

        assertEquals(2, second.contractVersion());
        assertEquals(LoanContractStatus.PREPARED, second.status());
        assertEquals(first.financialTerms(), second.financialTerms());
        assertEquals(
                first.repaymentItems().stream()
                        .map(item -> List.of(
                                item.installmentNumber(), item.principalDue(), item.interestDue(),
                                item.feeDue(), item.totalDue()
                        ))
                        .toList(),
                second.repaymentItems().stream()
                        .map(item -> List.of(
                                item.installmentNumber(), item.principalDue(), item.interestDue(),
                                item.feeDue(), item.totalDue()
                        ))
                        .toList()
        );
        assertFalse(first.disbursementBankAccount().sourceBankAccountId().equals(
                second.disbursementBankAccount().sourceBankAccountId()
        ));
        assertEquals(refreshedAccount.customerBankAccountId(),
                second.disbursementBankAccount().sourceBankAccountId());
        assertNull(second.acknowledgedAt());
        assertEquals("SUPERSEDED", text("SELECT status FROM loan_contracts WHERE id = ?", first.id()));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", accepted.applicationId));
    }

    @Test
    void concurrentUclManualDisbursementsCreateOneCompleteActivation() throws Exception {
        ReadyUcl readyUcl = readyUcl();
        LocalDate valueDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstRepaymentDate = valueDate.plusMonths(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> disburseAfter(
                    readyUcl, UUID.randomUUID(), "UCL-CONCURRENT-A-" + readyUcl.applicationId,
                    valueDate, firstRepaymentDate, ready, start
            ));
            Future<CommandOutcome> second = executor.submit(() -> disburseAfter(
                    readyUcl, UUID.randomUUID(), "UCL-CONCURRENT-B-" + readyUcl.applicationId,
                    valueDate, firstRepaymentDate, ready, start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        BusinessStateConflictException loser = assertInstanceOf(
                BusinessStateConflictException.class,
                outcomes.stream().filter(outcome -> !outcome.successful())
                        .map(CommandOutcome::failure).findFirst().orElseThrow()
        );
        assertEquals("DISBURSEMENT_ALREADY_COMPLETED", loser.getErrorCode());
        assertEquals("DISBURSED", status(readyUcl.applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_accounts WHERE loan_application_id = ?",
                readyUcl.applicationId));
        assertEquals(1, count("SELECT count(*) FROM manual_disbursements "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
        assertEquals(1, count("SELECT count(*) FROM repayment_schedules "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                readyUcl.applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'MANUAL_DISBURSEMENT_CONFIRMED' "
                + "AND payload ->> 'loanApplicationId' = ?",
                readyUcl.applicationId.toString()));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
    }

    @Test
    void failedMandatoryActivationAuditRollsBackEveryUclActivationEffect() {
        ReadyUcl readyUcl = readyUcl();
        useAccounting();
        doThrow(new IllegalStateException("simulated mandatory activation audit failure"))
                .when(auditPublisher)
                .publish(argThat(this::containsManualDisbursementAudit));

        LocalDate valueDate = LocalDate.now(ZoneOffset.UTC);
        assertThrows(IllegalStateException.class, () -> confirmManualDisbursementUseCase.confirm(
                new ConfirmManualDisbursementUseCase.Command(
                        UUID.randomUUID(), readyUcl.applicationId, readyUcl.contractVersion,
                        "UCL-ROLLBACK-" + readyUcl.applicationId, valueDate,
                        valueDate.plusMonths(1)
                )
        ));

        assertEquals("DISBURSEMENT_PENDING", status(readyUcl.applicationId));
        assertEquals("READY_FOR_DISBURSEMENT", text(
                "SELECT status FROM loan_contracts WHERE id = ?", readyUcl.contractId
        ));
        assertEquals(0, count("SELECT count(*) FROM loan_accounts WHERE loan_application_id = ?",
                readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM manual_disbursements "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM repayment_schedules "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM repayment_installment_progress progress "
                + "JOIN repayment_schedule_items item ON item.id = progress.repayment_schedule_item_id "
                + "JOIN repayment_schedules schedule ON schedule.id = item.repayment_schedule_id "
                + "WHERE schedule.loan_application_id = ?", readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_account_status_transitions history "
                + "JOIN loan_accounts account ON account.id = history.loan_account_id "
                + "WHERE account.loan_application_id = ?", readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM repayment_installment_status_transitions history "
                + "JOIN repayment_schedule_items item ON item.id = history.repayment_schedule_item_id "
                + "JOIN repayment_schedules schedule ON schedule.id = item.repayment_schedule_id "
                + "WHERE schedule.loan_application_id = ?", readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                readyUcl.applicationId));
        assertEquals(0, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'MANUAL_DISBURSEMENT_CONFIRMED' "
                + "AND payload ->> 'loanApplicationId' = ?",
                readyUcl.applicationId.toString()));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", readyUcl.applicationId));
    }

    @Test
    void concurrentUclApprovalsCreateOneDecisionAndOneOffer() throws Exception {
        UUID applicationId = originateToApprovalPending();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> approveAfter(applicationId, ready, start));
            Future<CommandOutcome> second = executor.submit(() -> approveAfter(applicationId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
        assertEquals(6, count("SELECT count(*) FROM approved_offer_repayment_items item "
                + "JOIN approved_offers offer ON offer.id = item.approved_offer_id "
                + "WHERE offer.loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'APPROVE'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'GENERATE_APPROVED_OFFER'", applicationId));

        useApprover();
        BusinessStateConflictException replayFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> decisionUseCase.submitApprovalDecision(
                        applicationId,
                        new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
                )
        );
        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", replayFailure.getErrorCode());
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
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
        assertEquals("PRODUCT_VERIFICATION_NOT_PENDING", failure.getErrorCode());
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
    void concurrentMoreInformationCompletionsCreateOneCorrectionRequestAndTask() throws Exception {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> completeVerificationAfter(
                    applicationId,
                    LOAN_OFFICER_USER_ID,
                    moreInformationRequest(evidence, "First concurrent correction assessment."),
                    ready,
                    start
            ));
            Future<CommandOutcome> second = executor.submit(() -> completeVerificationAfter(
                    applicationId,
                    SECOND_STAFF_USER_ID,
                    moreInformationRequest(evidence, "Second concurrent correction assessment."),
                    ready,
                    start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'CORRECTION_REQUEST_CREATED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));
    }

    @Test
    void concurrentUclResubmissionsCreateAtMostOneNextVerificationCycle() throws Exception {
        UUID applicationId = prepareReadyCorrection();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CommandOutcome> outcomes;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> resubmitAfter(
                    applicationId, UUID.randomUUID(), ready, start
            ));
            Future<CommandOutcome> second = executor.submit(() -> resubmitAfter(
                    applicationId, UUID.randomUUID(), ready, start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(CommandOutcome::successful).count());
        assertEquals("SUBMITTED", status(applicationId));
        assertEquals(2, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ? AND status = 'RESUBMITTED'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'RESUBMIT_CORRECTION'", applicationId));
    }

    @Test
    void uclCancellationAndResubmissionRaceHasOneWinningOutcome() throws Exception {
        UUID applicationId = prepareReadyCorrection();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CommandOutcome cancellation;
        CommandOutcome resubmission;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> cancelFuture = executor.submit(() -> cancelAfter(
                    applicationId, UUID.randomUUID(), ready, start
            ));
            Future<CommandOutcome> resubmitFuture = executor.submit(() -> resubmitAfter(
                    applicationId, UUID.randomUUID(), ready, start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            cancellation = cancelFuture.get(20, TimeUnit.SECONDS);
            resubmission = resubmitFuture.get(20, TimeUnit.SECONDS);
        }

        assertEquals(1, List.of(cancellation, resubmission).stream()
                .filter(CommandOutcome::successful).count());
        assertTrue(Set.of("CANCELLED", "SUBMITTED").contains(status(applicationId)));
        assertTrue(count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId) <= 2);
        assertTrue(count("SELECT count(*) FROM loan_application_cancellations "
                + "WHERE loan_application_id = ?", applicationId) <= 1);
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
    }

    @Test
    void failedVerificationIsDurableTerminalEvidenceWithoutCorrection() {
        UUID applicationId = originateAndMakeProcessingReady();
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);

        UnsecuredConsumerLoanVerificationDto failed = verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest(
                        UnsecuredConsumerLoanManualVerificationOutcome.FAILED,
                        "The submitted identity and employment evidence could not be verified.",
                        null,
                        null
                )
        );

        assertEquals("VERIFICATION_FAILED", failed.status());
        assertEquals("FAILED", failed.productVerificationResult());
        assertEquals(0, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ? AND product_verification_result = 'FAILED' "
                + "AND reviewed_by_user_id = ? AND assessment_note IS NOT NULL",
                applicationId, LOAN_OFFICER_USER_ID));
        BusinessRuleViolationException reviewFailure = assertThrows(
                BusinessRuleViolationException.class,
                () -> reviewStartUseCase.startReview(applicationId)
        );
        assertEquals("PRODUCT_VERIFICATION_FAILED", reviewFailure.getErrorCode());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications SET assessment_note = 'Mutated' "
                        + "WHERE loan_application_id = ?",
                applicationId
        ));
    }

    @Test
    void moreInformationCorrectionRoundTripCreatesLatestReverificationAndOffer() {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);

        UnsecuredConsumerLoanVerificationDto moreInformation = verificationUseCase
                .completeManualVerification(
                        applicationId,
                        moreInformationRequest(evidence, "Replace the selected UCL evidence.")
                );

        assertEquals("RETURNED_FOR_REVISION", moreInformation.status());
        assertEquals("REQUIRES_MORE_INFORMATION", moreInformation.productVerificationResult());
        assertEquals(1, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ? AND source_action = 'COMPLETE_PRODUCT_VERIFICATION' "
                + "AND status = 'OPEN'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ? AND task.document_type = ?",
                applicationId, evidence.documentType().name()));

        useCustomer();
        CustomerCorrectionTaskDto task = onlyCustomerTask(applicationId);
        DocumentVersionDto replacement = upload(
                applicationId,
                task.checklistItemId(),
                evidence.documentVersionId(),
                "ucl-replacement.pdf"
        );
        correctionTaskCompletion.complete(
                applicationId,
                task.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );

        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                task.checklistItemId(),
                replacement.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted UCL replacement acceptance note.",
                LOAN_OFFICER_USER_ID,
                false
        ));

        useCustomer();
        assertEquals("SUBMITTED", correctionResubmission.resubmit(
                applicationId,
                new CorrectionResubmissionRequest(UUID.randomUUID())
        ).loanApplicationStatus());
        assertEquals(2, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals("PENDING_MANUAL_REVIEW", text(
                "SELECT product_verification_result FROM unsecured_consumer_loan_verifications "
                        + "WHERE loan_application_id = ? ORDER BY verification_sequence DESC LIMIT 1",
                applicationId
        ));
        assertEquals(2, count("SELECT max(verification_sequence) "
                + "FROM unsecured_consumer_loan_verifications WHERE loan_application_id = ?",
                applicationId));

        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest(
                        "Replacement evidence is now consistent."
                )
        );
        assertEquals("UNDER_REVIEW", reviewStartUseCase.startReview(applicationId).status());
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL,
                        null,
                        null
                )
        );
        assertEquals("APPROVAL_PENDING", status(applicationId));
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers "
                + "WHERE loan_application_id = ?", applicationId));
    }

    @Test
    void loanOfficerUclCorrectionPreservesOldCycleAndRequiresReverification() {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest("Initial evidence is verified.")
        );
        reviewStartUseCase.startReview(applicationId);
        UUID firstCycle = uuid("SELECT id FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ? AND status = 'ACTIVE'", applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                        null,
                        "Restricted Loan Officer correction note.",
                        firstCycle,
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        replacementPlan(evidence)
                )
        );
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals("CORRECTION_REQUIRED", text(
                "SELECT status FROM loan_application_review_cycles WHERE id = ?",
                firstCycle
        ));

        completeCustomerReplacement(applicationId, evidence, "ucl-review-correction.pdf");
        useCustomer();
        correctionResubmission.resubmit(
                applicationId,
                new CorrectionResubmissionRequest(UUID.randomUUID())
        );
        assertEquals("SUBMITTED", status(applicationId));
        assertEquals("CORRECTED", text(
                "SELECT status FROM loan_application_review_cycles WHERE id = ?",
                firstCycle
        ));
        assertEquals(2, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));

        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest("Corrected evidence is verified.")
        );
        reviewStartUseCase.startReview(applicationId);
        UUID secondCycle = uuid("SELECT id FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ? AND status = 'ACTIVE'", applicationId);
        assertFalse(firstCycle.equals(secondCycle));
        assertEquals(2, count("SELECT count(*) FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId));
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        assertEquals("APPROVAL_PENDING", status(applicationId));
    }

    @Test
    void approverMixedUclCorrectionReturnsThroughVerificationAndNewReview() {
        UUID applicationId = originateToApprovalPending();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        UUID firstCycle = uuid("SELECT id FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId);
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(
                        ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                        null,
                        "Restricted Approver correction note.",
                        firstCycle,
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        mixedReplacementAndReviewPlan(evidence)
                )
        );
        assertEquals("RETURNED_FOR_REVISION", status(applicationId));
        assertEquals(2, count("SELECT count(*) FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ?", applicationId));

        useCustomer();
        CustomerCorrectionTaskDto customerTask = onlyCustomerTask(applicationId);
        DocumentVersionDto replacement = upload(
                applicationId,
                customerTask.checklistItemId(),
                evidence.documentVersionId(),
                "ucl-approval-correction.pdf"
        );
        correctionTaskCompletion.complete(
                applicationId,
                customerTask.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                customerTask.checklistItemId(),
                replacement.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted approval-correction acceptance.",
                LOAN_OFFICER_USER_ID,
                false
        ));
        UUID staffTaskId = uuid("SELECT task.id FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ? "
                + "AND task.responsible_party = 'STAFF'", applicationId);
        useBackOfficeStaff();
        StaffCorrectionTaskDto completedStaffTask = staffTaskCompletion.complete(
                staffTaskId,
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        assertEquals("COMPLETED", completedStaffTask.status());
        assertEquals("SUBMITTED", staffCorrectionResubmission.resubmitAsStaff(
                applicationId,
                new CorrectionResubmissionRequest(UUID.randomUUID())
        ).loanApplicationStatus());

        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                new CompleteUnsecuredConsumerLoanVerificationRequest(
                        "Approver-requested correction is verified."
                )
        );
        reviewStartUseCase.startReview(applicationId);
        recommendationUseCase.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL,
                        null,
                        null
                )
        );
        assertEquals("APPROVAL_PENDING", status(applicationId));
        assertEquals(2, count("SELECT count(*) FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(2, count("SELECT count(*) FROM unsecured_consumer_loan_verifications "
                + "WHERE loan_application_id = ?", applicationId));
    }

    @Test
    void uclReturnedCorrectionCancellationHasNoSalaryEffectAndReplaysExactly() {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                moreInformationRequest(evidence, "Cancellation-path replacement request.")
        );

        useCustomer();
        UUID requestId = UUID.randomUUID();
        CancelLoanApplicationUseCase.Result cancelled = cancellationUseCase.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, applicationId)
        );
        CancelLoanApplicationUseCase.Result replay = cancellationUseCase.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, applicationId)
        );

        assertEquals("CANCELLED", status(applicationId));
        assertFalse(cancelled.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(1, count("SELECT count(*) FROM loan_application_cancellations "
                + "WHERE loan_application_id = ? AND reservation_release_movement_id IS NULL",
                applicationId));
        assertEquals("CANCELLED", text("SELECT status FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'CANCEL_APPLICATION'", applicationId));
        assertEquals(1, count("SELECT count(*) FROM audit_events "
                + "WHERE action = 'LOAN_APPLICATION_CANCELLED' "
                + "AND payload ->> 'loanApplicationId' = ?", applicationId.toString()));
    }

    @Test
    void failedMoreInformationCompletionRollsBackCorrectionAndDecisionEvidence() {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        doThrow(new IllegalStateException("simulated completion audit failure"))
                .when(auditPublisher)
                .publish(argThat(this::containsCompletionAudit));

        assertThrows(IllegalStateException.class, () -> verificationUseCase.completeManualVerification(
                applicationId,
                moreInformationRequest(evidence, "Rollback this correction request.")
        ));

        assertEquals("VERIFICATION_PENDING", status(applicationId));
        assertEquals("PENDING_MANUAL_REVIEW", text(
                "SELECT product_verification_result FROM unsecured_consumer_loan_verifications "
                        + "WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals(0, count("SELECT count(*) FROM loan_correction_requests "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_correction_tasks task "
                + "JOIN loan_correction_requests correction ON correction.id = task.correction_request_id "
                + "WHERE correction.loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ? AND action = 'COMPLETE_PRODUCT_VERIFICATION'",
                applicationId));
        assertEquals(0, count("SELECT count(*) FROM audit_events "
                + "WHERE payload ->> 'loanApplicationId' = ? "
                + "AND action IN ('CORRECTION_REQUEST_CREATED', "
                + "'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED')",
                applicationId.toString()));
    }

    @Test
    void v43PreservesPendingRowsRejectsPartialOrUnevidencedTerminalRows() {
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

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE unsecured_consumer_loan_verifications SET product_verification_result = 'FAILED' "
                        + "WHERE loan_application_id = ?",
                application.loanApplicationId()
        ));
    }

    private AcceptedUcl acceptedUcl() {
        UUID applicationId = originateToApprovalPending();
        useApprover();
        decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        useCustomer();
        ApprovedOfferDto offer = queryApprovedOfferUseCase.getApprovedOffer(applicationId);
        respondToApprovedOfferUseCase.acceptOffer(applicationId);
        return new AcceptedUcl(applicationId, offer);
    }

    private ReadyUcl readyUcl() {
        AcceptedUcl accepted = acceptedUcl();
        useAccounting();
        LoanContract prepared = prepareLoanContractUseCase.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), accepted.applicationId, 0, null
                )
        );
        useCustomer();
        acknowledgeLoanContractUseCase.acknowledge(
                new AcknowledgeLoanContractUseCase.Command(
                        UUID.randomUUID(), accepted.applicationId, prepared.contractVersion()
                )
        );
        useAccounting();
        LoanContract ready = confirmContractReadinessUseCase.confirm(
                new ConfirmContractReadinessUseCase.Command(
                        UUID.randomUUID(), accepted.applicationId, prepared.id(),
                        prepared.contractVersion()
                )
        );
        return new ReadyUcl(accepted.applicationId, ready.id(), ready.contractVersion());
    }

    private UUID originateToApprovalPending() {
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
        return applicationId;
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
        return upload(applicationId, checklistItemId, null, "ucl-evidence.pdf");
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

    private CorrectionEvidence correctionEvidence(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT item.id, document.current_version_id, item.document_type "
                        + "FROM document_checklist_items item "
                        + "JOIN document_checklists checklist ON checklist.id = item.checklist_id "
                        + "JOIN documents document ON document.checklist_item_id = item.id "
                        + "WHERE checklist.loan_application_id = ? "
                        + "ORDER BY item.document_type LIMIT 1",
                (resultSet, rowNumber) -> new CorrectionEvidence(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("current_version_id", UUID.class),
                        DocumentType.valueOf(resultSet.getString("document_type"))
                ),
                applicationId
        );
    }

    private CompleteUnsecuredConsumerLoanVerificationRequest moreInformationRequest(
            CorrectionEvidence evidence,
            String assessmentNote
    ) {
        return new CompleteUnsecuredConsumerLoanVerificationRequest(
                UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                assessmentNote,
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(evidence)
        );
    }

    private CorrectionPlanRequest replacementPlan(CorrectionEvidence evidence) {
        return new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                evidence.documentType(),
                false,
                evidence.checklistItemId(),
                evidence.documentVersionId(),
                "Replace this UCL evidence with a clearer current version.",
                null
        )));
    }

    private CorrectionPlanRequest mixedReplacementAndReviewPlan(CorrectionEvidence evidence) {
        return new CorrectionPlanRequest(List.of(
                new CorrectionTaskRequest(
                        CorrectionScope.DOCUMENT_REPLACEMENT,
                        CorrectionResponsibility.CUSTOMER,
                        evidence.documentType(),
                        false,
                        evidence.checklistItemId(),
                        evidence.documentVersionId(),
                        "Replace this UCL evidence with a clearer current version.",
                        null
                ),
                new CorrectionTaskRequest(
                        CorrectionScope.DOCUMENT_REVIEW,
                        CorrectionResponsibility.STAFF,
                        evidence.documentType(),
                        false,
                        evidence.checklistItemId(),
                        evidence.documentVersionId(),
                        null,
                        "Independently review the replacement UCL evidence."
                )
        ));
    }

    private void completeCustomerReplacement(
            UUID applicationId,
            CorrectionEvidence evidence,
            String filename
    ) {
        useCustomer();
        CustomerCorrectionTaskDto task = onlyCustomerTask(applicationId);
        DocumentVersionDto replacement = upload(
                applicationId,
                task.checklistItemId(),
                evidence.documentVersionId(),
                filename
        );
        correctionTaskCompletion.complete(
                applicationId,
                task.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                task.checklistItemId(),
                replacement.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted UCL correction acceptance.",
                LOAN_OFFICER_USER_ID,
                false
        ));
    }

    private CustomerCorrectionTaskDto onlyCustomerTask(UUID applicationId) {
        List<CustomerCorrectionTaskDto> tasks = correctionTaskQuery.findOwnTasks(applicationId);
        assertEquals(1, tasks.size());
        return tasks.getFirst();
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

    private CommandOutcome completeVerificationAfter(
            UUID applicationId,
            UUID actorId,
            CompleteUnsecuredConsumerLoanVerificationRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useStaff(actorId);
        return afterBarrier(ready, start, () -> verificationUseCase.completeManualVerification(
                applicationId,
                request
        ));
    }

    private CommandOutcome resubmitAfter(
            UUID applicationId,
            UUID requestId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useCustomer();
        return afterBarrier(ready, start, () -> correctionResubmission.resubmit(
                applicationId,
                new CorrectionResubmissionRequest(requestId)
        ));
    }

    private CommandOutcome cancelAfter(
            UUID applicationId,
            UUID requestId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useCustomer();
        return afterBarrier(ready, start, () -> cancellationUseCase.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, applicationId)
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

    private CommandOutcome approveAfter(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useApprover();
        return afterBarrier(ready, start, () -> decisionUseCase.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        ));
    }

    private CommandOutcome disburseAfter(
            ReadyUcl readyUcl,
            UUID requestId,
            String transferReference,
            LocalDate valueDate,
            LocalDate firstRepaymentDate,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        useAccounting();
        return afterBarrier(ready, start, () -> confirmManualDisbursementUseCase.confirm(
                new ConfirmManualDisbursementUseCase.Command(
                        requestId,
                        readyUcl.applicationId,
                        readyUcl.contractVersion,
                        transferReference,
                        valueDate,
                        firstRepaymentDate
                )
        ));
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

    private boolean containsManualDisbursementAudit(BusinessAuditEvent event) {
        return event != null && event.entries().stream().anyMatch(
                entry -> entry.action() == BusinessAuditAction.MANUAL_DISBURSEMENT_CONFIRMED
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
                Set.of(
                        "loan:submit",
                        "document:upload:own",
                        "loan:read:own",
                        "loan:offer:respond:own",
                        "loan:correction:own",
                        "loan:cancel:own"
                )
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

    private void useAccounting() {
        currentUserProvider.use(new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting@meridian.local",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:contract:prepare", "repayment:update", "loan:account:close")
        ));
    }

    private void useBackOfficeStaff() {
        currentUserProvider.use(new AuthenticatedUser(
                SECOND_STAFF_USER_ID,
                "back-office@meridian.local",
                "STAFF",
                null,
                Set.of("BACK_OFFICE_ADMIN"),
                Set.of("loan:correction:staff", "document:review")
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

    private static LocalDate anchoredDate(LocalDate firstDate, int monthOffset) {
        YearMonth month = YearMonth.from(firstDate).plusMonths(monthOffset);
        return month.atDay(Math.min(firstDate.getDayOfMonth(), month.lengthOfMonth()));
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

    private UUID prepareReadyCorrection() {
        UUID applicationId = originateAndMakeProcessingReady();
        CorrectionEvidence evidence = correctionEvidence(applicationId);
        useLoanOfficer();
        verificationUseCase.startManualVerification(applicationId);
        verificationUseCase.completeManualVerification(
                applicationId,
                moreInformationRequest(evidence, "Prepare a correction for a race test.")
        );

        useCustomer();
        CustomerCorrectionTaskDto task = onlyCustomerTask(applicationId);
        DocumentVersionDto replacement = upload(
                applicationId,
                task.checklistItemId(),
                evidence.documentVersionId(),
                "ucl-race-replacement.pdf"
        );
        correctionTaskCompletion.complete(
                applicationId,
                task.correctionTaskId(),
                new CompleteCorrectionTaskRequest(UUID.randomUUID())
        );
        useLoanOfficer();
        documentReviewUseCase.review(new ReviewDocumentCommand(
                applicationId,
                task.checklistItemId(),
                replacement.documentVersionId(),
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT,
                null,
                "Restricted race-test replacement acceptance.",
                LOAN_OFFICER_USER_ID,
                false
        ));
        return applicationId;
    }

    private record CorrectionEvidence(
            UUID checklistItemId,
            UUID documentVersionId,
            DocumentType documentType
    ) {
    }

    private record AcceptedUcl(UUID applicationId, ApprovedOfferDto offer) {
    }

    private record ReadyUcl(UUID applicationId, UUID contractId, int contractVersion) {
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

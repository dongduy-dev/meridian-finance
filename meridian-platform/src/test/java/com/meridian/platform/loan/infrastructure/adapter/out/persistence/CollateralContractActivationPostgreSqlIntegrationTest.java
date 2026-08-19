package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.CollateralType;
import com.meridian.platform.loan.domain.model.ContractReadinessBlockerCode;
import com.meridian.platform.loan.domain.model.ContractSupersessionReason;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                CollateralLoanManualVerificationPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class,
                CollateralContractActivationPostgreSqlIntegrationTest.FixedClockConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.loan.overdue-evaluation.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CollateralContractActivationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "mer_cl_cp4_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), TEST_SCHEMA + "_documents"
    ).toString();
    private static final UUID LOAN_OFFICER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 8, 19);
    private static final LocalDate FIRST_REPAYMENT_DATE = LocalDate.of(2026, 8, 31);
    private static final byte[] PDF = "%PDF-1.7\n% Meridian Collateral CP4 evidence\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private StartCollateralLoanApplicationUseCase submission;
    @Autowired private UploadDocumentUseCase uploads;
    @Autowired private ReviewDocumentUseCase documentReviews;
    @Autowired private ManageCollateralLoanVerificationUseCase verifications;
    @Autowired private StartLoanApplicationReviewUseCase reviews;
    @Autowired private SubmitReviewRecommendationUseCase recommendations;
    @Autowired private SubmitApprovalDecisionUseCase decisions;
    @Autowired private RespondToApprovedOfferUseCase offerResponses;
    @Autowired private ManageOwnCustomerBankAccountUseCase bankAccounts;
    @Autowired private PrepareLoanContractUseCase contractPreparation;
    @Autowired private AcknowledgeLoanContractUseCase acknowledgments;
    @Autowired private QueryContractReadinessUseCase readinessQuery;
    @Autowired private ConfirmContractReadinessUseCase readinessConfirmation;
    @Autowired private RevealDisbursementDestinationUseCase destinationReveal;
    @Autowired private ConfirmManualDisbursementUseCase disbursements;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CollateralLoanManualVerificationPostgreSqlIntegrationTest.ThreadLocalCurrentUserProvider currentUser;
    @MockitoSpyBean private CollateralLoanVerificationRepository collateralVerifications;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + TEST_SCHEMA);
        registry.add("meridian.document.storage-root", () -> STORAGE_ROOT);
    }

    @BeforeEach
    void setUp() {
        reset(collateralVerifications);
    }

    @AfterEach
    void clearUser() {
        currentUser.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24})
    void activatesAcceptedCollateralOfferWithoutRepricingForEveryApprovedTerm(int termMonths) {
        Fixture fixture = acceptedOfferFixture(termMonths);
        List<Map<String, Object>> offerTerms = offerTerms(fixture.applicationId());
        List<Map<String, Object>> offerItems = offerItems(fixture.applicationId());
        int partnerCompaniesBefore = count("select count(*) from partner_companies");
        int partnerEmployeesBefore = count("select count(*) from partner_employees");

        useAccounting();
        UUID preparationRequestId = UUID.randomUUID();
        LoanContract prepared = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        preparationRequestId, fixture.applicationId(), 0, null
                )
        );
        LoanContract preparationReplay = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        preparationRequestId, fixture.applicationId(), 0, null
                )
        );

        assertEquals(prepared.id(), preparationReplay.id());
        assertEquals(1, count(
                "select count(*) from loan_contracts where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(offerTerms, contractTerms(fixture.applicationId(), 1));
        assertEquals(offerItems, contractItems(fixture.applicationId(), 1));
        assertEquals(new BigDecimal("0.015000"),
                prepared.financialTerms().flatMonthlyInterestRate());
        assertEquals("5678", prepared.disbursementBankAccount().lastFour());
        assertFalse(prepared.toString().contains(fixture.accountNumber()));
        assertFalse(prepared.disbursementBankAccount().toString().contains(fixture.accountNumber()));

        useCustomer(fixture);
        LoanContract acknowledged = acknowledgments.acknowledge(
                new AcknowledgeLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 1
                )
        );
        assertEquals(LoanContractStatus.ACKNOWLEDGED, acknowledged.status());

        useAccounting();
        QueryContractReadinessUseCase.Snapshot ready = readinessQuery.query(
                fixture.applicationId(), 1
        );
        assertTrue(ready.ready());
        LoanContract confirmed = readinessConfirmation.confirm(
                new ConfirmContractReadinessUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1
                )
        );
        assertEquals(LoanContractStatus.READY_FOR_DISBURSEMENT, confirmed.status());
        RevealDisbursementDestinationUseCase.Result revealed = destinationReveal.reveal(
                new RevealDisbursementDestinationUseCase.Command(fixture.applicationId(), 1)
        );
        assertEquals(fixture.accountNumber(), revealed.accountNumber());

        ConfirmManualDisbursementUseCase.Command command =
                new ConfirmManualDisbursementUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 1,
                        "COLLATERAL-CP4-" + UUID.randomUUID(), VALUE_DATE,
                        FIRST_REPAYMENT_DATE
                );
        ConfirmManualDisbursementUseCase.Result activated = disbursements.confirm(command);
        ConfirmManualDisbursementUseCase.Result replay = disbursements.confirm(command);

        assertEquals(LoanApplicationStatus.DISBURSED, activated.applicationStatus());
        assertEquals("ACTIVE", text(
                "select status from loan_accounts where id=?", activated.loanAccountId()
        ));
        assertTrue(replay.idempotentReplay());
        assertEquals(activated.loanAccountId(), replay.loanAccountId());
        assertEquals(activated.manualDisbursementId(), replay.manualDisbursementId());
        assertEquals(activated.repaymentScheduleId(), replay.repaymentScheduleId());
        assertEquals(1, count(
                "select count(*) from loan_accounts where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(1, count(
                "select count(*) from manual_disbursements where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(1, count(
                "select count(*) from repayment_schedules where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(termMonths, activated.scheduleItems().size());
        assertEquals("FINAL", text(
                "select schedule_type from repayment_schedules where id=?",
                activated.repaymentScheduleId()
        ));
        assertEquals(1, integer(
                "select version from repayment_schedules where id=?",
                activated.repaymentScheduleId()
        ));
        assertEquals(FIRST_REPAYMENT_DATE, date(
                "select first_due_date from repayment_schedules where id=?",
                activated.repaymentScheduleId()
        ));
        assertFinalScheduleCopiesContract(activated, prepared);
        assertAccountCopiesContract(activated.loanAccountId(), prepared);
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(partnerCompaniesBefore, count("select count(*) from partner_companies"));
        assertEquals(partnerEmployeesBefore, count("select count(*) from partner_employees"));
        assertEquals(0, count(
                "select count(*) from audit_events where entity_id=? and payload::text like '%5678%'",
                fixture.applicationId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update manual_disbursements set first_repayment_date=first_repayment_date where id=?",
                activated.manualDisbursementId()
        ));
    }

    @Test
    void collateralVerificationFailsClosedBeforeCaptureAndAtReadiness() {
        Fixture fixture = acceptedOfferFixture(6);
        List<Optional<CollateralLoanVerification>> invalid = List.of(
                Optional.empty(),
                Optional.of(verification(fixture.applicationId(),
                        ProductVerificationResult.PENDING_MANUAL_REVIEW)),
                Optional.of(verification(fixture.applicationId(), ProductVerificationResult.FAILED)),
                Optional.of(verification(fixture.applicationId(),
                        ProductVerificationResult.REQUIRES_MORE_INFORMATION))
        );
        useAccounting();
        for (Optional<CollateralLoanVerification> evidence : invalid) {
            doReturn(evidence).when(collateralVerifications)
                    .findLatestByLoanApplicationId(fixture.applicationId());
            BusinessStateConflictException failure = assertThrows(
                    BusinessStateConflictException.class,
                    () -> contractPreparation.prepare(
                            new PrepareLoanContractUseCase.Command(
                                    UUID.randomUUID(), fixture.applicationId(), 0, null
                            )
                    )
            );
            assertEquals("COLLATERAL_VERIFICATION_INVALID", failure.getErrorCode());
            assertEquals(0, count(
                    "select count(*) from loan_contracts where loan_application_id=?",
                    fixture.applicationId()
            ));
        }

        reset(collateralVerifications);
        LoanContract prepared = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 0, null
                )
        );
        useCustomer(fixture);
        acknowledgments.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 1
        ));
        useAccounting();
        doReturn(Optional.empty()).when(collateralVerifications)
                .findLatestByLoanApplicationId(fixture.applicationId());

        QueryContractReadinessUseCase.Snapshot blocked = readinessQuery.query(
                fixture.applicationId(), 1
        );
        assertFalse(blocked.ready());
        assertTrue(blocked.blockers().contains(
                ContractReadinessBlockerCode.COLLATERAL_VERIFICATION_INVALID
        ));
        BusinessStateConflictException confirmationFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> readinessConfirmation.confirm(
                        new ConfirmContractReadinessUseCase.Command(
                                UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1
                        )
                )
        );
        assertEquals("COLLATERAL_VERIFICATION_INVALID", confirmationFailure.getErrorCode());
        assertEquals("CONTRACT_PENDING", status(fixture.applicationId()));
        assertEquals("ACKNOWLEDGED", text(
                "select status from loan_contracts where id=?", prepared.id()
        ));
    }

    @Test
    void destinationRefreshSupersedesContractWithoutChangingFinancialSnapshot() {
        Fixture fixture = acceptedOfferFixture(12);
        useAccounting();
        LoanContract first = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 0, null
                )
        );
        useCustomer(fixture);
        acknowledgments.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 1
        ));
        CustomerBankAccountDto secondAccount = bankAccounts.addBankAccount(
                new AddCustomerBankAccountRequest(
                        "SECOND", "Second Test Bank", "COLLATERAL CP4 CUSTOMER",
                        "9988776655443322"
                )
        );
        bankAccounts.makePrimary(secondAccount.customerBankAccountId());

        useAccounting();
        LoanContract refreshed = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 1,
                        ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH
                )
        );

        assertEquals(2, refreshed.contractVersion());
        assertEquals("SUPERSEDED", text(
                "select status from loan_contracts where id=?", first.id()
        ));
        assertEquals("PREPARED", text(
                "select status from loan_contracts where id=?", refreshed.id()
        ));
        assertEquals(contractTerms(fixture.applicationId(), 1),
                contractTerms(fixture.applicationId(), 2));
        assertEquals(contractItems(fixture.applicationId(), 1),
                contractItems(fixture.applicationId(), 2));
        assertEquals("3322", refreshed.disbursementBankAccount().lastFour());
        QueryContractReadinessUseCase.Snapshot acknowledgmentBlocked = readinessQuery.query(
                fixture.applicationId(), 2
        );
        assertTrue(acknowledgmentBlocked.blockers().contains(
                ContractReadinessBlockerCode.ACKNOWLEDGMENT_MISSING
        ));

        useCustomer(fixture);
        acknowledgments.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 2
        ));
        useAccounting();
        readinessConfirmation.confirm(new ConfirmContractReadinessUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), refreshed.id(), 2
        ));
        assertEquals("9988776655443322", destinationReveal.reveal(
                new RevealDisbursementDestinationUseCase.Command(fixture.applicationId(), 2)
        ).accountNumber());
        BusinessStateConflictException readyRegeneration = assertThrows(
                BusinessStateConflictException.class,
                () -> contractPreparation.prepare(new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 2,
                        ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH
                ))
        );
        assertEquals("INVALID_APPLICATION_STATE", readyRegeneration.getErrorCode());
    }

    @Test
    void invalidFirstRepaymentDatesRollbackEveryActivationWrite() {
        Fixture fixture = acceptedOfferFixture(6);
        LoanContract ready = prepareAcknowledgeAndConfirm(fixture);

        for (LocalDate invalidDate : List.of(VALUE_DATE, VALUE_DATE.plusMonths(1).plusDays(1))) {
            BusinessRuleViolationException failure = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> disbursements.confirm(new ConfirmManualDisbursementUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), ready.contractVersion(),
                            "INVALID-DATE-" + UUID.randomUUID(), VALUE_DATE, invalidDate
                    ))
            );
            assertEquals("FIRST_REPAYMENT_DATE_INVALID", failure.getErrorCode());
            assertNoActivation(fixture.applicationId());
        }
    }

    private Fixture acceptedOfferFixture(int termMonths) {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        createReadyCustomer(customerId, customerUserId);
        Fixture customer = new Fixture(customerId, customerUserId, null, null, termMonths);
        useCustomer(customer);
        String accountNumber = "12345678905678";
        CustomerBankAccountDto bankAccount = bankAccounts.addBankAccount(
                new AddCustomerBankAccountRequest(
                        "TEST", "Test Bank", "COLLATERAL CP4 CUSTOMER", accountNumber
                )
        );
        CollateralLoanApplicationDto application = submission.startCollateralLoanApplication(
                new CollateralLoanApplicationRequest(
                        new BigDecimal("25000000"), termMonths,
                        new CollateralDetailsRequest(
                                CollateralType.CAR, "Customer vehicle",
                                new BigDecimal("50000000"),
                                "Customer-submitted ownership statement", "Normal used condition"
                        )
                )
        );
        UUID applicationId = application.loanApplicationId();
        UUID checklistItemId = application.evidenceRequirements().getFirst().checklistItemId();
        DocumentVersionDto version = uploads.upload(new UploadDocumentCommand(
                applicationId, checklistItemId, UUID.randomUUID(), null,
                "collateral-ownership-evidence.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), DocumentUploaderActorType.CUSTOMER,
                customerUserId, customerId
        ));
        useLoanOfficer();
        documentReviews.review(new ReviewDocumentCommand(
                applicationId, checklistItemId, version.documentVersionId(), UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT, null,
                "Restricted Collateral ownership evidence review.",
                LOAN_OFFICER_USER_ID, false
        ));
        CollateralLoanVerificationStartDto started = verifications.startManualVerification(
                applicationId
        );
        verifications.completeManualVerification(
                applicationId,
                new CompleteCollateralLoanVerificationRequest(
                        started.verificationId(), CollateralLoanManualVerificationOutcome.VERIFIED,
                        "Ownership evidence and Collateral facts were assessed.", null, null
                )
        );
        reviews.startReview(applicationId);
        recommendations.submitReviewRecommendation(
                applicationId,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null
                )
        );
        useApprover();
        decisions.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        useCustomer(customer);
        offerResponses.acceptOffer(applicationId);
        assertEquals("CONTRACT_PENDING", status(applicationId));
        return new Fixture(
                customerId, customerUserId, applicationId, bankAccount.customerBankAccountId(),
                termMonths, accountNumber
        );
    }

    private LoanContract prepareAcknowledgeAndConfirm(Fixture fixture) {
        useAccounting();
        LoanContract prepared = contractPreparation.prepare(
                new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(), 0, null
                )
        );
        useCustomer(fixture);
        acknowledgments.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), 1
        ));
        useAccounting();
        return readinessConfirmation.confirm(new ConfirmContractReadinessUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), prepared.id(), 1
        ));
    }

    private void assertFinalScheduleCopiesContract(
            ConfirmManualDisbursementUseCase.Result activated,
            LoanContract contract
    ) {
        assertEquals(contract.repaymentItems().size(), activated.scheduleItems().size());
        for (int index = 0; index < activated.scheduleItems().size(); index++) {
            ConfirmManualDisbursementUseCase.ScheduleItem actual =
                    activated.scheduleItems().get(index);
            var expected = contract.repaymentItems().get(index);
            assertEquals(expected.id(), actual.sourceLoanContractRepaymentItemId());
            assertEquals(expected.installmentNumber(), actual.installmentNumber());
            assertEquals(0, expected.principalDue().compareTo(actual.principalDue()));
            assertEquals(0, expected.interestDue().compareTo(actual.interestDue()));
            assertEquals(0, expected.feeDue().compareTo(actual.feeDue()));
            assertEquals(0, expected.totalDue().compareTo(actual.totalDue()));
            YearMonth month = YearMonth.from(FIRST_REPAYMENT_DATE).plusMonths(index);
            LocalDate expectedDate = month.atDay(Math.min(31, month.lengthOfMonth()));
            assertEquals(expectedDate, actual.dueDate());
        }
    }

    private void assertAccountCopiesContract(UUID accountId, LoanContract contract) {
        Map<String, Object> account = jdbc.queryForMap(
                "select approved_principal,approved_term_months,total_interest,fee_amount,"
                        + "total_repayment_amount from loan_accounts where id=?",
                accountId
        );
        assertEquals(0, contract.financialTerms().approvedPrincipal().compareTo(
                (BigDecimal) account.get("approved_principal")
        ));
        assertEquals(contract.financialTerms().approvedTermMonths(),
                account.get("approved_term_months"));
        assertEquals(0, contract.financialTerms().totalInterest().compareTo(
                (BigDecimal) account.get("total_interest")
        ));
        assertEquals(0, contract.financialTerms().feeAmount().compareTo(
                (BigDecimal) account.get("fee_amount")
        ));
        assertEquals(0, contract.financialTerms().totalRepaymentAmount().compareTo(
                (BigDecimal) account.get("total_repayment_amount")
        ));
    }

    private void assertNoActivation(UUID applicationId) {
        assertEquals("DISBURSEMENT_PENDING", status(applicationId));
        assertEquals(0, count(
                "select count(*) from loan_accounts where loan_application_id=?", applicationId
        ));
        assertEquals(0, count(
                "select count(*) from manual_disbursements where loan_application_id=?",
                applicationId
        ));
        assertEquals(0, count(
                "select count(*) from repayment_schedules where loan_application_id=?",
                applicationId
        ));
        assertEquals(0, count(
                "select count(*) from loan_application_status_transitions "
                        + "where loan_application_id=? and action='CONFIRM_MANUAL_DISBURSEMENT'",
                applicationId
        ));
        assertEquals(0, count(
                "select count(*) from audit_events "
                        + "where entity_id=? and action='MANUAL_DISBURSEMENT_CONFIRMED'",
                applicationId
        ));
    }

    private List<Map<String, Object>> offerTerms(UUID applicationId) {
        return jdbc.queryForList(
                "select approved_principal,approved_term_months,interest_calculation_method,"
                        + "flat_monthly_interest_rate,total_interest,fee_amount,"
                        + "total_repayment_amount,repayment_method "
                        + "from approved_offers where loan_application_id=?",
                applicationId
        );
    }

    private List<Map<String, Object>> contractTerms(UUID applicationId, int version) {
        return jdbc.queryForList(
                "select approved_principal,approved_term_months,interest_calculation_method,"
                        + "flat_monthly_interest_rate,total_interest,fee_amount,"
                        + "total_repayment_amount,repayment_method "
                        + "from loan_contracts where loan_application_id=? and contract_version=?",
                applicationId, version
        );
    }

    private List<Map<String, Object>> offerItems(UUID applicationId) {
        return jdbc.queryForList(
                "select item.installment_number,item.principal_due,item.interest_due,"
                        + "item.fee_due,item.total_due from approved_offer_repayment_items item "
                        + "join approved_offers offer on offer.id=item.approved_offer_id "
                        + "where offer.loan_application_id=? order by item.installment_number",
                applicationId
        );
    }

    private List<Map<String, Object>> contractItems(UUID applicationId, int version) {
        return jdbc.queryForList(
                "select item.installment_number,item.principal_due,item.interest_due,"
                        + "item.fee_due,item.total_due from loan_contract_repayment_items item "
                        + "join loan_contracts contract on contract.id=item.loan_contract_id "
                        + "where contract.loan_application_id=? and contract.contract_version=? "
                        + "order by item.installment_number",
                applicationId, version
        );
    }

    private CollateralLoanVerification verification(
            UUID applicationId,
            ProductVerificationResult result
    ) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 18, 8, 0);
        if (result == ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            return new CollateralLoanVerification(
                    UUID.randomUUID(), applicationId, result, createdAt
            );
        }
        return new CollateralLoanVerification(
                UUID.randomUUID(), applicationId, 1, null, result, createdAt,
                LOAN_OFFICER_USER_ID, createdAt.plusHours(1),
                "Restricted Collateral verification evidence."
        );
    }

    private void createReadyCustomer(UUID customerId, UUID customerUserId) {
        String unique = customerId.toString().replace("-", "");
        jdbc.update(
                "insert into customers "
                        + "(id,customer_number,status,verification_status,profile_completion_status) "
                        + "values (?,?,'ACTIVE','UNVERIFIED','COMPLETE')",
                customerId, "CL-CP4-" + unique.substring(0, 12)
        );
        jdbc.update(
                "insert into customer_profiles "
                        + "(id,customer_id,full_name,identity_reference_ciphertext,"
                        + "identity_reference_fingerprint,identity_reference_last_four,phone_number,"
                        + "residential_address,employment_status,employer_name,"
                        + "terms_consent_accepted,data_processing_consent_accepted) "
                        + "values (?,?,'Collateral CP4 Customer','protected-test-value',?,'1234',"
                        + "'0900000000','Test Address','EMPLOYED','Test Employer',true,true)",
                UUID.randomUUID(), customerId, "identity-" + unique
        );
        jdbc.update(
                "insert into users "
                        + "(id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) "
                        + "values (?,?,?,'test-password-hash','CUSTOMER','ACTIVE',"
                        + "'Collateral CP4 Customer',?)",
                customerUserId, "cl-cp4-" + unique + "@meridian.test",
                "cl-cp4-" + unique + "@meridian.test", customerId
        );
    }

    private void useCustomer(Fixture fixture) {
        currentUser.use(new AuthenticatedUser(
                fixture.customerUserId(), "collateral-cp4-customer@meridian.test", "CUSTOMER",
                fixture.customerId(), Set.of("CUSTOMER"),
                Set.of("loan:submit", "document:upload:own", "loan:read:own",
                        "loan:contract:acknowledge:own")
        ));
    }

    private void useLoanOfficer() {
        currentUser.use(new AuthenticatedUser(
                LOAN_OFFICER_USER_ID, "collateral-cp4-officer@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review", "approval:recommend", "document:review")
        ));
    }

    private void useApprover() {
        currentUser.use(new AuthenticatedUser(
                APPROVER_USER_ID, "collateral-cp4-approver@meridian.test", "STAFF", null,
                Set.of("APPROVER"), Set.of("approval:decide")
        ));
    }

    private void useAccounting() {
        currentUser.use(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "collateral-cp4-accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:contract:prepare", "loan:contract:readiness", "loan:disburse")
        ));
    }

    private String status(UUID applicationId) {
        return text("select status from loan_applications where id=?", applicationId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private Integer integer(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private LocalDate date(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, LocalDate.class, arguments);
    }

    private record Fixture(
            UUID customerId,
            UUID customerUserId,
            UUID applicationId,
            UUID bankAccountId,
            int termMonths,
            String accountNumber
    ) {
        Fixture(
                UUID customerId,
                UUID customerUserId,
                UUID applicationId,
                UUID bankAccountId,
                int termMonths
        ) {
            this(customerId, customerUserId, applicationId, bankAccountId, termMonths, null);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock collateralCp4Clock() {
            return Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}

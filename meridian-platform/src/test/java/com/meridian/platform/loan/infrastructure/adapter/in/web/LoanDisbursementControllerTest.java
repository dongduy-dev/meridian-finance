package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.mapper.LoanDisbursementApiMapper;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanDisbursementControllerTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private StubUseCases useCases;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCases = new StubUseCases();
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanDisbursementController(
                        useCases, useCases, useCases, new LoanDisbursementApiMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsConfirmationCommandAndReturnsOnlySafeActivationEvidence() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "expectedContractVersion": 1,
                                  "externalTransferReference": "  bank-ref-01  ",
                                  "disbursementValueDate": "2026-07-28",
                                  "firstRepaymentDate": "2026-08-28"
                                }
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.applicationStatus").value("DISBURSED"))
                .andExpect(jsonPath("$.loanAccountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.scheduleType").value("FINAL"))
                .andExpect(jsonPath("$.scheduleItems[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.externalTransferReference").doesNotExist())
                .andExpect(jsonPath("$.destination").doesNotExist())
                .andExpect(jsonPath("$.actorId").doesNotExist());

        assertEquals(requestId, useCases.confirmCommand.requestId());
        assertEquals(APPLICATION_ID, useCases.confirmCommand.loanApplicationId());
        assertEquals("BANK-REF-01", useCases.confirmCommand.externalTransferReference());
    }

    @Test
    void revealUsesDedicatedSensitiveShapeAndNoStoreHeaders() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/disbursement-destination/reveal",
                        APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedContractVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.accountNumber").value("01234567890"))
                .andExpect(jsonPath("$.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.nonce").doesNotExist())
                .andExpect(jsonPath("$.keyId").doesNotExist())
                .andExpect(jsonPath("$.fingerprint").doesNotExist());
        assertEquals(APPLICATION_ID, useCases.revealCommand.loanApplicationId());
    }

    @Test
    void queryReturnsMaskedContractDestinationAndOrderedFinalSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disbursementDestination.maskedAccountNumber")
                        .value("********"))
                .andExpect(jsonPath("$.finalRepaymentSchedule.scheduleType").value("FINAL"))
                .andExpect(jsonPath("$.finalRepaymentSchedule.items[0].installmentNumber")
                        .value(1))
                .andExpect(jsonPath("$.accountNumber")
                        .value("LN-30000000000000000000000000000001"))
                .andExpect(jsonPath("$.externalTransferReference").doesNotExist())
                .andExpect(jsonPath("$.ciphertext").doesNotExist());
        assertEquals(APPLICATION_ID, useCases.queryApplicationId);
    }

    @Test
    void validatesRequestsAndMapsStableBusinessErrors() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedContractVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        useCases.confirmFailure = new BusinessStateConflictException(
                "DUPLICATE_TRANSFER_REFERENCE", "Transfer evidence already exists.");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmationBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TRANSFER_REFERENCE"));


        useCases.confirmFailure = new EntityNotFoundException(
                "CURRENT_CONTRACT_MISSING", "Current Loan contract is missing.");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmationBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_CONTRACT_MISSING"));
        useCases.confirmFailure = new BusinessRuleViolationException(
                "FIRST_REPAYMENT_DATE_INVALID", "First repayment date is invalid.");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmationBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FIRST_REPAYMENT_DATE_INVALID"));

        useCases.confirmFailure = null;
        useCases.queryFailure = new EntityNotFoundException(
                "LOAN_ACCOUNT_NOT_FOUND", "Loan Account was not found.");
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_ACCOUNT_NOT_FOUND"));
    }

    private static String validConfirmationBody() {
        return """
                {
                  "requestId": "40000000-0000-0000-0000-000000000001",
                  "expectedContractVersion": 1,
                  "externalTransferReference": "BANK-REF-01",
                  "disbursementValueDate": "2026-07-28",
                  "firstRepaymentDate": "2026-08-28"
                }
                """;
    }

    private static final class StubUseCases implements
            ConfirmManualDisbursementUseCase,
            RevealDisbursementDestinationUseCase,
            QueryLoanAccountUseCase {

        private ConfirmManualDisbursementUseCase.Command confirmCommand;
        private RevealDisbursementDestinationUseCase.Command revealCommand;
        private UUID queryApplicationId;
        private RuntimeException confirmFailure;
        private RuntimeException queryFailure;

        @Override
        public ConfirmManualDisbursementUseCase.Result confirm(
                ConfirmManualDisbursementUseCase.Command command
        ) {
            confirmCommand = command;
            if (confirmFailure != null) {
                throw confirmFailure;
            }
            return confirmationResult();
        }

        @Override
        public RevealDisbursementDestinationUseCase.Result reveal(
                RevealDisbursementDestinationUseCase.Command command
        ) {
            revealCommand = command;
            return new RevealDisbursementDestinationUseCase.Result(
                    APPLICATION_ID,
                    UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    1, "VCB", "Example Bank", "MERIDIAN CUSTOMER", "01234567890"
            );
        }

        @Override
        public QueryLoanAccountUseCase.Result query(UUID loanApplicationId) {
            queryApplicationId = loanApplicationId;
            if (queryFailure != null) {
                throw queryFailure;
            }
            return queryResult();
        }
    }

    private static ConfirmManualDisbursementUseCase.Result confirmationResult() {
        return new ConfirmManualDisbursementUseCase.Result(
                APPLICATION_ID, LoanApplicationStatus.DISBURSED,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "LN-30000000000000000000000000000001", LoanAccountStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 28, 10, 0),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                new BigDecimal("3000000"), LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 28),
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                RepaymentScheduleType.FINAL, 1,
                List.of(new ConfirmManualDisbursementUseCase.ScheduleItem(
                        UUID.randomUUID(), UUID.randomUUID(), 1,
                        LocalDate.of(2026, 8, 28), new BigDecimal("3000000"),
                        new BigDecimal("120000"), BigDecimal.ZERO,
                        new BigDecimal("3120000"))), false
        );
    }

    private static QueryLoanAccountUseCase.Result queryResult() {
        return new QueryLoanAccountUseCase.Result(
                APPLICATION_ID,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "LN-30000000000000000000000000000001", LoanAccountStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 28, 10, 0), new BigDecimal("3000000"),
                1, new BigDecimal("120000"), BigDecimal.ZERO,
                new BigDecimal("3120000"),
                new QueryLoanAccountUseCase.ServicingSummary(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("3000000"), new BigDecimal("120000"),
                        BigDecimal.ZERO, new BigDecimal("3120000"),
                        LocalDate.of(2026, 7, 28), null, null),
                new QueryLoanAccountUseCase.DestinationSummary(
                        "VCB", "Example Bank", "MERIDIAN CUSTOMER", "********"),
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                RepaymentScheduleType.FINAL, 1, LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 28),
                List.of(new QueryLoanAccountUseCase.ScheduleItem(
                        1, LocalDate.of(2026, 8, 28), new BigDecimal("3000000"),
                        new BigDecimal("120000"), BigDecimal.ZERO,
                        new BigDecimal("3120000"),
                        new QueryLoanAccountUseCase.InstallmentServicing(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                new BigDecimal("3000000"), new BigDecimal("120000"),
                                BigDecimal.ZERO, new BigDecimal("3120000"),
                                RepaymentInstallmentStatus.NOT_DUE, LocalDate.of(2026, 7, 28),
                                null, null)))
        );
    }
}

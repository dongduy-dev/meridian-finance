package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.RecordRepaymentRequest;
import com.meridian.platform.loan.application.mapper.LoanRepaymentApiMapper;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanRepaymentControllerTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private StubUseCases useCases;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCases = new StubUseCases();
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanRepaymentController(
                        useCases, useCases, new LoanRepaymentApiMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsSafeRecordResponseAndUsesPathApplicationIdentity() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "externalPaymentReference": "  pay-ref-01  ",
                                  "amount": 100,
                                  "paymentValueDate": "2026-08-01"
                                }
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.principalAllocated").value(100))
                .andExpect(jsonPath("$.principalReleased").value(100))
                .andExpect(jsonPath("$.allocations[0].sequence").value(1))
                .andExpect(jsonPath("$.affectedInstallments[0].dueDate")
                        .value("2026-08-28"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.externalPaymentReference").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.actorId").doesNotExist());

        assertEquals(APPLICATION_ID, useCases.recordCommand.loanApplicationId());
        assertEquals(10, useCases.recordCommand.externalPaymentReference().length());
        assertTrue(useCases.recordCommand.externalPaymentReference()
                .chars().allMatch(character -> Character.isUpperCase(character)
                        || Character.isDigit(character) || character == '-'));
    }

    @Test
    void returnsReplayAsSafeOkResponse() throws Exception {
        useCases.replay = true;
        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    @Test
    void preservesUclContractualPrincipalAndZeroExposureRelease() throws Exception {
        useCases.zeroPrincipalRelease = true;

        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalAllocated").value(100))
                .andExpect(jsonPath("$.principalReleased").value(0));
    }

    @Test
    void mapsDefaultAndExplicitHistoryPagination() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].repaymentTransactionId").exists())
                .andExpect(jsonPath("$.items[0].idempotentReplay").doesNotExist())
                .andExpect(jsonPath("$.items[0].externalPaymentReference").doesNotExist());
        assertEquals(0, useCases.page);
        assertEquals(20, useCases.size);

        mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments?page=2&size=100",
                        APPLICATION_ID))
                .andExpect(status().isOk());
        assertEquals(2, useCases.page);
        assertEquals(100, useCases.size);
    }

    @Test
    void rejectsInvalidPageAndRedactsReferenceFromRequestAndErrors() throws Exception {

        String secret = "REFERENCE-THAT-MUST-NOT-LEAK";
        RecordRepaymentRequest request = new RecordRepaymentRequest(
                UUID.randomUUID(), secret, money(100), LocalDate.of(2026, 8, 1));
        assertFalse(request.toString().contains(secret));

        useCases.failure = new BusinessStateConflictException(
                "DUPLICATE_PAYMENT_REFERENCE", "External payment evidence was already recorded.");
        String response = mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("PAY-REF-01", secret)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(secret));
    }

    private static String validBody() {
        return """
                {
                  "requestId": "40000000-0000-0000-0000-000000000001",
                  "externalPaymentReference": "PAY-REF-01",
                  "amount": 100,
                  "paymentValueDate": "2026-08-01"
                }
                """;
    }

    private final class StubUseCases implements RecordRepaymentUseCase, QueryRepaymentsUseCase {
        private Command recordCommand;
        private boolean replay;
        private boolean zeroPrincipalRelease;
        private int page;
        private int size;
        private RuntimeException failure;

        @Override
        public Result record(Command command) {
            recordCommand = command;
            if (failure != null) {
                throw failure;
            }
            return repaymentResult(replay, zeroPrincipalRelease);
        }

        @Override
        public PageResult query(UUID loanApplicationId, int requestedPage, int requestedSize) {
            page = requestedPage;
            size = requestedSize;
            RecordRepaymentUseCase.Result result = repaymentResult(false, false);
            return new PageResult(page, size, 1, 1, List.of(new Item(
                    result.repaymentTransactionId(), result.receivedAmount(),
                    result.paymentValueDate(), result.recordedAt(), money(100), money(100),
                    LoanAccountStatus.ACTIVE, result.accountBalance(), result.allocations(),
                    result.installmentProgress())));
        }
    }

    private static RecordRepaymentUseCase.Result repaymentResult(
            boolean replay,
            boolean zeroPrincipalRelease
    ) {
        UUID itemId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        LocalDate date = LocalDate.of(2026, 8, 1);
        LocalDateTime recordedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        var balance = new RecordRepaymentUseCase.AccountBalance(
                money(100), money(0), money(0), money(100), money(900), money(0),
                money(0), money(900), date, recordedAt, date, LoanAccountStatus.ACTIVE);
        var progress = new RecordRepaymentUseCase.InstallmentProgress(
                itemId, 1, LocalDate.of(2026, 8, 28), money(100), money(0), money(0),
                money(100), money(900), money(0), money(0), money(900),
                RepaymentInstallmentStatus.NOT_DUE, RepaymentInstallmentStatus.PARTIALLY_PAID,
                date, recordedAt, date, true);
        return new RecordRepaymentUseCase.Result(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                money(100), date, recordedAt, List.of(new RecordRepaymentUseCase.Allocation(
                1, itemId, 1, RepaymentAllocationComponent.PRINCIPAL, money(100))),
                List.of(progress), balance, money(100),
                zeroPrincipalRelease ? money(0) : money(100), replay);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}

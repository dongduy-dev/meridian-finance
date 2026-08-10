package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ApproveLoanSettlementRequest;
import com.meridian.platform.loan.application.mapper.LoanSettlementApiMapper;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanSettlementControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );

    private StubSettlementUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubSettlementUseCase();
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanSettlementController(
                        useCase,
                        new LoanSettlementApiMapper()
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsPathIdentityAndReturnsOnlySafeSettlementFacts() throws Exception {
        UUID requestId = UUID.randomUUID();
        String secretReference = "BANK-REFERENCE-MUST-REMAIN-INTERNAL";

        String response = mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "expectedSettlementAmount": 1230000,
                                  "paymentValueDate": "2026-09-01",
                                  "externalPaymentReference": "%s"
                                }
                                """.formatted(requestId, secretReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId")
                        .value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.settlementAmount").value(1230000))
                .andExpect(jsonPath("$.resultingLoanAccountStatus").value("SETTLED"))
                .andExpect(jsonPath("$.accountBalance.totalOutstanding").value(0))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.externalPaymentReference").doesNotExist())
                .andExpect(jsonPath("$.actorId").doesNotExist())
                .andExpect(jsonPath("$.approvedByUserId").doesNotExist())
                .andExpect(jsonPath("$.settlementId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertEquals(APPLICATION_ID, useCase.command.loanApplicationId());
        assertEquals("BANK-REFERENCE-MUST-REMAIN-INTERNAL",
                useCase.command.externalPaymentReference());
        assertFalse(response.contains(secretReference));
        assertFalse(new ApproveLoanSettlementRequest(
                requestId,
                money("1230000"),
                LocalDate.of(2026, 9, 1),
                secretReference
        ).toString().contains(secretReference));
        assertFalse(useCase.command.toString().contains(requestId.toString()));
    }

    @Test
    void returnsSafeConflictWithoutLeakingSubmittedReference() throws Exception {
        String secretReference = "CONFLICTING-SECRET-REFERENCE";
        useCase.failure = new BusinessStateConflictException(
                "DUPLICATE_PAYMENT_REFERENCE",
                "External payment evidence was already recorded."
        );

        String response = mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("SETTLEMENT-REF", secretReference)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("DUPLICATE_PAYMENT_REFERENCE"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(secretReference));
    }

    private static String validBody() {
        return """
                {
                  "requestId": "40000000-0000-0000-0000-000000000001",
                  "expectedSettlementAmount": 1230000,
                  "paymentValueDate": "2026-09-01",
                  "externalPaymentReference": "SETTLEMENT-REF"
                }
                """;
    }

    private static ApproveLoanSettlementUseCase.Result result(boolean replay) {
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        return new ApproveLoanSettlementUseCase.Result(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                money("1230000"),
                date,
                approvedAt,
                money("1200000"),
                new ApproveLoanSettlementUseCase.AccountBalance(
                        money("1200000"), money("30000"), money("0"),
                        money("1230000"), money("0"), money("0"), money("0"),
                        money("0"), date, approvedAt, date,
                        LoanAccountStatus.SETTLED
                ),
                replay
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static final class StubSettlementUseCase
            implements ApproveLoanSettlementUseCase {
        private Command command;
        private RuntimeException failure;

        @Override
        public Result approve(Command submitted) {
            command = submitted;
            if (failure != null) {
                throw failure;
            }
            return result(false);
        }
    }
}

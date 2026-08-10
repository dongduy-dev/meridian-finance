package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.mapper.LoanAccountClosureApiMapper;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanAccountClosureControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private StubClosureUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubClosureUseCase();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LoanAccountClosureController(
                                useCase,
                                new LoanAccountClosureApiMapper()
                        ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsPathIdentityAndReturnsOnlySafeLifecycleFacts() throws Exception {
        UUID requestId = UUID.randomUUID();

        String response = mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/loan-account/closure",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s"}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId")
                        .value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.resultingStatus").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").value("2026-09-02T10:00:00"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.closureId").doesNotExist())
                .andExpect(jsonPath("$.actorId").doesNotExist())
                .andExpect(jsonPath("$.repaymentTransactionId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertEquals(APPLICATION_ID, useCase.command.loanApplicationId());
        assertFalse(response.contains(requestId.toString()));
        assertFalse(useCase.command.toString().contains(requestId.toString()));
    }

    @Test
    void mapsClosureNotAllowedToSafeConflict() throws Exception {
        useCase.failure = new BusinessStateConflictException(
                "LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED",
                "Administrative closure is not allowed for the Loan Account."
        );

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/loan-account/closure",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"40000000-0000-0000-0000-000000000001"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED"));
    }

    private static CloseLoanAccountUseCase.Result result(boolean replay) {
        return new CloseLoanAccountUseCase.Result(
                APPLICATION_ID,
                UUID.randomUUID(),
                LoanAccountStatus.CLOSED,
                LocalDateTime.of(2026, 9, 2, 10, 0),
                replay
        );
    }

    private static final class StubClosureUseCase
            implements CloseLoanAccountUseCase {
        private Command command;
        private RuntimeException failure;

        @Override
        public Result close(Command submitted) {
            command = submitted;
            if (failure != null) {
                throw failure;
            }
            return result(false);
        }
    }
}

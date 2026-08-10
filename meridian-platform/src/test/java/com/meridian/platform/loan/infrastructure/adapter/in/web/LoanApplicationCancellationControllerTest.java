package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.mapper.LoanApplicationCancellationApiMapper;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
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

class LoanApplicationCancellationControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private StubCancellationUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubCancellationUseCase();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LoanApplicationCancellationController(
                                useCase,
                                new LoanApplicationCancellationApiMapper()
                        ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsPathIdentityAndReturnsOnlySafeCancellationFacts() throws Exception {
        UUID requestId = UUID.randomUUID();

        String response = mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/cancel",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s"}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId")
                        .value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.resultingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-08-10T09:00:00"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.cancellationId").doesNotExist())
                .andExpect(jsonPath("$.correctionRequestId").doesNotExist())
                .andExpect(jsonPath("$.movementId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertEquals(APPLICATION_ID, useCase.command.loanApplicationId());
        assertFalse(response.contains(requestId.toString()));
        assertFalse(useCase.command.toString().contains(requestId.toString()));
    }

    @Test
    void mapsInvalidStateToSafeConflict() throws Exception {
        useCase.failure = new BusinessStateConflictException(
                "LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED",
                "Loan Application cancellation is not allowed in the current state."
        );

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/cancel",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"40000000-0000-0000-0000-000000000001"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED"));
    }

    private static CancelLoanApplicationUseCase.Result result() {
        return new CancelLoanApplicationUseCase.Result(
                APPLICATION_ID,
                LoanApplicationStatus.CANCELLED,
                LocalDateTime.of(2026, 8, 10, 9, 0),
                false
        );
    }

    private static final class StubCancellationUseCase
            implements CancelLoanApplicationUseCase {
        private Command command;
        private RuntimeException failure;

        @Override
        public Result cancel(Command submitted) {
            command = submitted;
            if (failure != null) {
                throw failure;
            }
            return result();
        }
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanApplicationReviewControllerTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private StubUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoanApplicationReviewController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void startsReview() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(LOAN_APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void returnsConflictWhenReviewCannotStart() throws Exception {
        useCase.failure = new BusinessStateConflictException(
                "LOAN_REVIEW_START_NOT_ALLOWED",
                "Only submitted loan applications can start Loan Officer review."
        );

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOAN_REVIEW_START_NOT_ALLOWED"));
    }

    private static class StubUseCase implements StartLoanApplicationReviewUseCase {

        private RuntimeException failure;

        @Override
        public LoanApplicationReviewDto startReview(UUID loanApplicationId) {
            if (failure != null) {
                throw failure;
            }
            return new LoanApplicationReviewDto(loanApplicationId, "UNDER_REVIEW");
        }
    }
}

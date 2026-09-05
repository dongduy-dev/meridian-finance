package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationReviewUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffLoanApplicationReviewControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    void serializesCurrentReviewCycleWithoutRecommendationOrDecisionEvidence() throws Exception {
        QueryStaffLoanApplicationReviewUseCase useCase = ignored -> new StaffLoanApplicationReviewDto(
                APPLICATION_ID,
                "UCL-20260905-000001",
                "UNSECURED_CONSUMER_LOAN",
                "UNSECURED",
                new BigDecimal("8000000"),
                6,
                "UNDER_REVIEW",
                LocalDateTime.of(2026, 9, 5, 8, 0),
                new StaffLoanApplicationReviewDto.DocumentReadinessDto(true, true),
                new StaffLoanApplicationReviewDto.ProductReadinessDto("VERIFIED", true),
                false,
                new StaffLoanApplicationReviewDto.ReviewCycleDto(
                        UUID.randomUUID(),
                        2,
                        "ACTIVE",
                        LocalDateTime.of(2026, 9, 5, 9, 0),
                        null
                )
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffLoanApplicationReviewController(useCase)
        ).build();

        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/review",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentReviewCycle.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewStartAvailable").value(false))
                .andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.reviewerUserId").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }
}

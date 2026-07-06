package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovedOfferControllerTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private StubUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApprovedOfferController(useCase, useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsApprovedOffer() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{loanApplicationId}/approved-offer", LOAN_APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(LOAN_APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.availableActions[0]").value("ACCEPT"));
    }

    @Test
    void mapsExpiredActionOutcomeToConflict() throws Exception {
        useCase.nextActionResult = new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.EXPIRED,
                offer("EXPIRED", List.of())
        );

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept", LOAN_APPLICATION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OFFER_EXPIRED"));
    }

    private static ApprovedOfferDto offer(String status, List<String> availableActions) {
        return new ApprovedOfferDto(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                LOAN_APPLICATION_ID,
                status,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                "FLAT_ORIGINAL_PRINCIPAL",
                new BigDecimal("0.012000"),
                BigDecimal.valueOf(36_000).setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.valueOf(3_036_000).setScale(2),
                "ON_SALARY_DATE",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                LocalDateTime.of(2026, 7, 13, 9, 0),
                null,
                null,
                null,
                availableActions,
                List.of()
        );
    }

    private static class StubUseCase implements QueryApprovedOfferUseCase, RespondToApprovedOfferUseCase {

        private ApprovedOfferActionResult nextActionResult = new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.SUCCESS,
                offer("ACCEPTED", List.of())
        );

        @Override
        public ApprovedOfferDto getApprovedOffer(UUID loanApplicationId) {
            return offer("PENDING", List.of("ACCEPT", "DECLINE"));
        }

        @Override
        public ApprovedOfferActionResult acceptOffer(UUID loanApplicationId) {
            return nextActionResult;
        }

        @Override
        public ApprovedOfferActionResult declineOffer(UUID loanApplicationId) {
            return nextActionResult;
        }
    }
}

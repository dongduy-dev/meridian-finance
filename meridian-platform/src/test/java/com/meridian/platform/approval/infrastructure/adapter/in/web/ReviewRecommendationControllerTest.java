package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewRecommendationControllerTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private StubUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewRecommendationController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void recordsRecommendation() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "RECOMMEND_APPROVAL",
                                  "internalNotes": "ready"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanApplicationId").value(LOAN_APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.loanOfficerUserId").value(LOAN_OFFICER_USER_ID.toString()))
                .andExpect(jsonPath("$.action").value("RECOMMEND_APPROVAL"));
    }

    @Test
    void returnsBadRequestWhenActionIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsUnprocessableEntityWhenDomainValidationFails() throws Exception {
        useCase.failure = new BusinessRuleViolationException(
                "RECOMMENDATION_REASON_REQUIRED",
                "A reason is required for this review recommendation action."
        );

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "REQUEST_STAFF_CORRECTION"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECOMMENDATION_REASON_REQUIRED"));
    }

    private static class StubUseCase implements SubmitReviewRecommendationUseCase {

        private RuntimeException failure;

        @Override
        public ReviewRecommendationDto submitReviewRecommendation(
                UUID loanApplicationId,
                ReviewRecommendationRequest request
        ) {
            if (failure != null) {
                throw failure;
            }
            return new ReviewRecommendationDto(
                    UUID.randomUUID(),
                    loanApplicationId,
                    LOAN_OFFICER_USER_ID,
                    request.action().name(),
                    request.reason(),
                    request.internalNotes(),
                    LocalDateTime.now()
            );
        }
    }
}

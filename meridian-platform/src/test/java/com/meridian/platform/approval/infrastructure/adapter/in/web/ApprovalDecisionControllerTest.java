package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
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

class ApprovalDecisionControllerTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    private StubUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApprovalDecisionController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void recordsApprovalDecision() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "internalNotes": "ready"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanApplicationId").value(LOAN_APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.reviewRecommendationId").value(RECOMMENDATION_ID.toString()))
                .andExpect(jsonPath("$.approverUserId").value(APPROVER_USER_ID.toString()))
                .andExpect(jsonPath("$.action").value("APPROVE"));
    }

    @Test
    void returnsBadRequestWhenActionIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsUnprocessableEntityWhenDomainValidationFails() throws Exception {
        useCase.failure = new BusinessRuleViolationException(
                "APPROVAL_DECISION_REASON_REQUIRED",
                "A reason is required for this approval decision action."
        );

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "REJECT"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_DECISION_REASON_REQUIRED"));
    }

    private static class StubUseCase implements SubmitApprovalDecisionUseCase {

        private RuntimeException failure;

        @Override
        public ApprovalDecisionDto submitApprovalDecision(
                UUID loanApplicationId,
                ApprovalDecisionRequest request
        ) {
            if (failure != null) {
                throw failure;
            }
            return new ApprovalDecisionDto(
                    UUID.randomUUID(),
                    loanApplicationId,
                    RECOMMENDATION_ID,
                    APPROVER_USER_ID,
                    request.action().name(),
                    request.reason(),
                    request.internalNotes(),
                    LocalDateTime.now()
            );
        }
    }
}

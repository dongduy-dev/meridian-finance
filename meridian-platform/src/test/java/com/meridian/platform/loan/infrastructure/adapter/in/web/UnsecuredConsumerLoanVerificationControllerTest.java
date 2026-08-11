package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnsecuredConsumerLoanVerificationControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private StubUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UnsecuredConsumerLoanVerificationController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void startsManualVerification() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("VERIFICATION_PENDING"))
                .andExpect(jsonPath("$.productVerificationResult").value("PENDING_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.assessmentNote").doesNotExist());
    }

    @Test
    void completesManualVerificationWithoutReturningInternalNoteOrActor() throws Exception {
        useCase.complete = true;

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentNote": "Income and employment evidence are consistent."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.productVerificationResult").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedAt").value("2026-08-11T09:00:00"))
                .andExpect(jsonPath("$.assessmentNote").doesNotExist())
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
    }

    @Test
    void rejectsBlankAssessmentNote() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentNote": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsGenericResultField() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentNote": "Evidence is consistent.",
                                  "result": "FAILED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static class StubUseCase implements ManageUnsecuredConsumerLoanVerificationUseCase {
        private boolean complete;

        @Override
        public UnsecuredConsumerLoanVerificationDto startManualVerification(UUID loanApplicationId) {
            return new UnsecuredConsumerLoanVerificationDto(
                    loanApplicationId,
                    "VERIFICATION_PENDING",
                    "PENDING_MANUAL_REVIEW",
                    null
            );
        }

        @Override
        public UnsecuredConsumerLoanVerificationDto completeManualVerification(
                UUID loanApplicationId,
                CompleteUnsecuredConsumerLoanVerificationRequest request
        ) {
            return new UnsecuredConsumerLoanVerificationDto(
                    loanApplicationId,
                    complete ? "SUBMITTED" : "VERIFICATION_PENDING",
                    complete ? "VERIFIED" : "PENDING_MANUAL_REVIEW",
                    complete ? LocalDateTime.of(2026, 8, 11, 9, 0) : null
            );
        }
    }
}

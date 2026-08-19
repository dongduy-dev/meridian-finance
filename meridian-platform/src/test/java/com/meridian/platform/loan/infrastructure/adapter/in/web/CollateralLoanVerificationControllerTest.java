package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollateralLoanVerificationControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VERIFICATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CollateralLoanVerificationController(new StubUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void startsVerificationWithStaffAssessmentSnapshot() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/start",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(VERIFICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("VERIFICATION_PENDING"))
                .andExpect(jsonPath("$.collateral.collateralType").value("CAR"))
                .andExpect(jsonPath("$.collateral.estimatedValue").value(25000000))
                .andExpect(jsonPath("$.assessmentNote").doesNotExist())
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
    }

    @Test
    void completesExactCycleWithoutReturningRestrictedEvidence() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVerificationId": "%s",
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "Ownership evidence is sufficient."
                                }
                                """.formatted(VERIFICATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(VERIFICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.productVerificationResult").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedAt").value("2026-08-19T09:00:00"))
                .andExpect(jsonPath("$.assessmentNote").doesNotExist())
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist())
                .andExpect(jsonPath("$.collateral").doesNotExist());
    }

    @Test
    void rejectsMissingExpectedVerificationId() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "Ownership evidence is sufficient."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsBlankOrOversizedAssessment() throws Exception {
        assertInvalidAssessment("   ");
        assertInvalidAssessment("x".repeat(2001));
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVerificationId": "%s",
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "Ownership evidence is sufficient.",
                                  "reviewerUserId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
                                }
                                """.formatted(VERIFICATION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private void assertInvalidAssessment(String assessmentNote) throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVerificationId": "%s",
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "%s"
                                }
                                """.formatted(VERIFICATION_ID, assessmentNote)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static class StubUseCase implements ManageCollateralLoanVerificationUseCase {

        @Override
        public CollateralLoanVerificationStartDto startManualVerification(UUID loanApplicationId) {
            return new CollateralLoanVerificationStartDto(
                    VERIFICATION_ID,
                    loanApplicationId,
                    "VERIFICATION_PENDING",
                    "PENDING_MANUAL_REVIEW",
                    new CollateralAssessmentSnapshotDto(
                            "CAR",
                            "Customer vehicle",
                            new BigDecimal("25000000"),
                            "Customer-owned",
                            "Operational condition"
                    )
            );
        }

        @Override
        public CollateralLoanVerificationDto completeManualVerification(
                UUID loanApplicationId,
                CompleteCollateralLoanVerificationRequest request
        ) {
            return new CollateralLoanVerificationDto(
                    VERIFICATION_ID,
                    loanApplicationId,
                    "SUBMITTED",
                    "VERIFIED",
                    LocalDateTime.of(2026, 8, 19, 9, 0)
            );
        }
    }
}

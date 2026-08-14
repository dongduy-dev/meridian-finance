package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.SubmissionEvidenceRequirementDto;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollateralLoanApplicationControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CollateralLoanApplicationController(new StubUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsCollateralLoanAndReturnsUploadTarget() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode").value("COLLATERAL_LOAN"))
                .andExpect(jsonPath("$.status").value("DOCUMENTS_PENDING"))
                .andExpect(jsonPath("$.productVerificationResult").value("PENDING_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.evidenceRequirements[0].checklistItemId")
                        .value("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                .andExpect(jsonPath("$.evidenceRequirements[0].documentType")
                        .value("COLLATERAL_OWNERSHIP_EVIDENCE"))
                .andExpect(jsonPath("$.evidenceRequirements[0].requirementStatus").value("REQUIRED"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void rejectsMissingInvalidAndUnknownFields() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "\"conditionNote\": \"Normal used condition\"",
                                "\"conditionNote\": \" \""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "\"requestedTermMonths\": 12,",
                                "\"requestedTermMonths\": 12, \"customerId\": \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\","
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "\"type\": \"MOTORBIKE\",",
                                "\"type\": \"MOTORBIKE\", \"ownershipDocumentId\": \"dddddddd-dddd-dddd-dddd-dddddddddddd\","
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private String validRequest() {
        return """
                {
                  "requestedAmount": 25000000,
                  "requestedTermMonths": 12,
                  "collateral": {
                    "type": "MOTORBIKE",
                    "description": "2024 Honda motorbike",
                    "estimatedValue": 35000000,
                    "ownershipStatus": "Customer-provided ownership statement",
                    "conditionNote": "Normal used condition"
                  }
                }
                """;
    }

    private static class StubUseCase implements StartCollateralLoanApplicationUseCase {
        @Override
        public CollateralLoanApplicationDto startCollateralLoanApplication(
                CollateralLoanApplicationRequest request
        ) {
            return new CollateralLoanApplicationDto(
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    "CL-20260813-000001",
                    "COLLATERAL_LOAN",
                    "SECURED",
                    "DOCUMENTS_PENDING",
                    new BigDecimal("25000000"),
                    12,
                    "MOTORBIKE",
                    "PENDING_MANUAL_REVIEW",
                    List.of(new SubmissionEvidenceRequirementDto(
                            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                            "COLLATERAL_OWNERSHIP_EVIDENCE",
                            "REQUIRED"
                    )),
                    LocalDateTime.parse("2026-08-13T09:00:00")
            );
        }
    }
}

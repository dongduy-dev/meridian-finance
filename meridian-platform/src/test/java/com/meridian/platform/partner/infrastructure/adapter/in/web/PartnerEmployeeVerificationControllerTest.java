package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.port.in.QueryOwnPartnerEmployeeVerificationUseCase;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerEmployeeVerificationControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final UUID partnerCompanyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PartnerEmployeeVerificationController(new StubUseCase(), new StubQueryUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsSafeVerificationResponseWithoutSalaryOrRawEmployeeEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/partner-companies/{partnerCompanyId}/employee-verifications", partnerCompanyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeCode": "MER-EMP-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.partnerCompanyId").value(partnerCompanyId.toString()))
                .andExpect(jsonPath("$.outcome").value("MATCHED_ACTIVE"))
                .andExpect(jsonPath("$.linkStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.salaryAmount").doesNotExist())
                .andExpect(jsonPath("$.salaryAdvanceLimit").doesNotExist())
                .andExpect(jsonPath("$.identityReference").doesNotExist())
                .andExpect(jsonPath("$.employeeCode").doesNotExist());
    }

    @Test
    void returnsOnlyCustomerSafeCurrentReviewStatesSeparatedByPartnerCompany() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies/employee-verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partnerCompanyId").value(partnerCompanyId.toString()))
                .andExpect(jsonPath("$[0].outcome").value("MANUAL_REVIEW_APPROVED"))
                .andExpect(jsonPath("$[0].manualReviewRequired").value(false))
                .andExpect(jsonPath("$[0].customerId").doesNotExist())
                .andExpect(jsonPath("$[0].reviewId").doesNotExist())
                .andExpect(jsonPath("$[0].reviewerUserId").doesNotExist())
                .andExpect(jsonPath("$[0].decisionReason").doesNotExist())
                .andExpect(jsonPath("$[0].partnerEmployeeId").doesNotExist())
                .andExpect(jsonPath("$[0].employeeCode").doesNotExist())
                .andExpect(jsonPath("$[0].salaryAmount").doesNotExist())
                .andExpect(jsonPath("$[0].salaryAdvanceLimit").doesNotExist())
                .andExpect(jsonPath("$[0].identityReference").doesNotExist())
                .andExpect(jsonPath("$[0].sourceImportBatchId").doesNotExist())
                .andExpect(jsonPath("$[0].candidates").doesNotExist())
                .andExpect(jsonPath("$[0].internalNotes").doesNotExist());
    }

    private static class StubUseCase implements VerifyPartnerEmployeeUseCase {

        @Override
        public PartnerEmployeeVerificationDto verifyPartnerEmployee(
                UUID partnerCompanyId,
                PartnerEmployeeVerificationRequest request
        ) {
            return new PartnerEmployeeVerificationDto(
                    CUSTOMER_ID,
                    partnerCompanyId,
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01"),
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    "MATCHED_ACTIVE",
                    "VERIFIED",
                    false
            );
        }
    }

    private class StubQueryUseCase implements QueryOwnPartnerEmployeeVerificationUseCase {

        @Override
        public List<OwnPartnerEmployeeVerificationDto> getCurrentOwnVerifications() {
            return List.of(new OwnPartnerEmployeeVerificationDto(
                    PartnerEmployeeVerificationControllerTest.this.partnerCompanyId,
                    "MANUAL_REVIEW_APPROVED",
                    false
            ));
        }
    }
}

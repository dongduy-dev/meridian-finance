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
    void returnsOnlyCustomerSafeLatestReviewState() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employee-verifications", partnerCompanyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerCompanyId").value(partnerCompanyId.toString()))
                .andExpect(jsonPath("$.outcome").value("MANUAL_REVIEW_APPROVED"))
                .andExpect(jsonPath("$.manualReviewRequired").value(false))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.reviewerUserId").doesNotExist())
                .andExpect(jsonPath("$.partnerEmployeeId").doesNotExist())
                .andExpect(jsonPath("$.salaryAmount").doesNotExist())
                .andExpect(jsonPath("$.identityReference").doesNotExist())
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.internalNotes").doesNotExist());
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

    private static class StubQueryUseCase implements QueryOwnPartnerEmployeeVerificationUseCase {

        @Override
        public OwnPartnerEmployeeVerificationDto getLatestOwnVerification(UUID partnerCompanyId) {
            return new OwnPartnerEmployeeVerificationDto(
                    partnerCompanyId,
                    "MANUAL_REVIEW_APPROVED",
                    false
            );
        }
    }
}

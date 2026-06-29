package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerEmployeeVerificationControllerTest {

    private final UUID partnerCompanyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PartnerEmployeeVerificationController(new StubUseCase()))
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
                                  "customerId": "99999999-9999-9999-9999-999999999999",
                                  "identityReference": "IDREF-MER-001",
                                  "employeeCode": "MER-EMP-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("99999999-9999-9999-9999-999999999999"))
                .andExpect(jsonPath("$.partnerCompanyId").value(partnerCompanyId.toString()))
                .andExpect(jsonPath("$.outcome").value("MATCHED_ACTIVE"))
                .andExpect(jsonPath("$.linkStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.salaryAmount").doesNotExist())
                .andExpect(jsonPath("$.salaryAdvanceLimit").doesNotExist())
                .andExpect(jsonPath("$.identityReference").doesNotExist())
                .andExpect(jsonPath("$.employeeCode").doesNotExist());
    }

    private static class StubUseCase implements VerifyPartnerEmployeeUseCase {

        @Override
        public PartnerEmployeeVerificationDto verifyPartnerEmployee(
                UUID partnerCompanyId,
                PartnerEmployeeVerificationRequest request
        ) {
            return new PartnerEmployeeVerificationDto(
                    request.customerId(),
                    partnerCompanyId,
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01"),
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    "MATCHED_ACTIVE",
                    "VERIFIED",
                    false
            );
        }
    }
}

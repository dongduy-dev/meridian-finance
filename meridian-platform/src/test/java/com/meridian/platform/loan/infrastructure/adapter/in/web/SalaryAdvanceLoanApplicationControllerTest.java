package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalaryAdvanceLoanApplicationControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SalaryAdvanceLoanApplicationController(new StubUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsBadRequestWhenRequestValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/salary-advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static class StubUseCase implements StartSalaryAdvanceApplicationUseCase {

        @Override
        public SalaryAdvanceApplicationDto startSalaryAdvanceApplication(SalaryAdvanceApplicationRequest request) {
            return new SalaryAdvanceApplicationDto(
                    UUID.randomUUID(),
                    "SA-20260626-000001",
                    CUSTOMER_ID,
                    "SALARY_ADVANCE",
                    "SALARY_BASED",
                    "SUBMITTED",
                    BigDecimal.valueOf(3_000_000).setScale(2),
                    1,
                    request.customerPartnerEmployeeLinkId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "VERIFIED",
                    BigDecimal.valueOf(6_000_000).setScale(2),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.valueOf(3_000_000).setScale(2),
                    BigDecimal.valueOf(3_000_000).setScale(2),
                    LocalDateTime.now()
            );
        }
    }
}

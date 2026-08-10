package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanApplicationControllerTest {

    @Test
    void returnsMinimalLifecycleStatusProjection() throws Exception {
        UUID applicationId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        QueryLoanApplicationUseCase useCase = ignored -> new LoanApplicationStatusDto(
                applicationId,
                "SA-20260810-000001",
                "SALARY_ADVANCE",
                "SALARY_BASED",
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                "UNDER_REVIEW",
                LocalDateTime.of(2026, 8, 10, 8, 0)
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LoanApplicationController(useCase))
                .build();

        mockMvc.perform(get("/api/v1/loan-applications/{loanApplicationId}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.salaryAdvanceLimitId").doesNotExist())
                .andExpect(jsonPath("$.salaryAdvanceVerificationId").doesNotExist())
                .andExpect(jsonPath("$.allowedActions").doesNotExist())
                .andExpect(jsonPath("$.nextActions").doesNotExist());
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.in.QuerySalaryAdvanceReadinessUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalaryAdvanceReadinessControllerTest {

    @Test
    void returnsSafeReadinessShapeWithoutInternalEvidenceIdentifiers() throws Exception {
        UUID linkId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        QuerySalaryAdvanceReadinessUseCase useCase = () -> new SalaryAdvanceReadinessDto(
                "SALARY_ADVANCE",
                linkId,
                "VERIFIED",
                "ELIGIBLE",
                "ACTIVE",
                amount(6_000_000),
                amount(1_000_000),
                amount(2_000_000),
                amount(3_000_000),
                LocalDateTime.of(2026, 8, 10, 8, 0),
                true,
                List.of()
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SalaryAdvanceReadinessController(useCase))
                .build();

        mockMvc.perform(get("/api/v1/loan-products/salary-advance/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("SALARY_ADVANCE"))
                .andExpect(jsonPath("$.customerPartnerEmployeeLinkId").value(linkId.toString()))
                .andExpect(jsonPath("$.availableAmount").value(3_000_000))
                .andExpect(jsonPath("$.applicationAllowed").value(true))
                .andExpect(jsonPath("$.salaryAdvanceLimitId").doesNotExist())
                .andExpect(jsonPath("$.partnerEmployeeId").doesNotExist())
                .andExpect(jsonPath("$.sourceImportBatchId").doesNotExist())
                .andExpect(jsonPath("$.salary").doesNotExist());
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}

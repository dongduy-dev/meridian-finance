package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.dto.CustomerLoanApplicationSummaryDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

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

    @Test
    void returnsCustomerApplicationIndexWithBackendActionAndNoOwnershipInput() throws Exception {
        UUID applicationId = UUID.randomUUID();
        QueryLoanApplicationUseCase useCase = new QueryLoanApplicationUseCase() {
            @Override public LoanApplicationStatusDto query(UUID ignored) {
                throw new UnsupportedOperationException();
            }
            @Override public List<CustomerLoanApplicationSummaryDto> queryOwnApplications() {
                return List.of(new CustomerLoanApplicationSummaryDto(
                        applicationId, "UCL-20260830-000001", "UNSECURED_CONSUMER_LOAN",
                        "UNSECURED", BigDecimal.valueOf(10_000_000), 3,
                        "DOCUMENTS_PENDING", LocalDateTime.of(2026, 8, 30, 8, 0),
                        true, CustomerLoanApplicationSummaryDto.CustomerApplicationAction
                        .UPLOAD_DOCUMENTS
                ));
            }
        };
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LoanApplicationController(useCase)).build();

        mockMvc.perform(get("/api/v1/loan-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanApplicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$[0].requiredAction").value("UPLOAD_DOCUMENTS"))
                .andExpect(jsonPath("$[0].lifecycleActive").value(true))
                .andExpect(jsonPath("$[0].customerId").doesNotExist())
                .andExpect(jsonPath("$[0].staffNotes").doesNotExist());
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerLoanAccountSummaryDto;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
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

class CustomerLoanAccountControllerTest {

    @Test
    void exposesCompactIndexWithoutDestinationOrStaffEvidence() throws Exception {
        UUID applicationId = UUID.randomUUID();
        QueryLoanAccountUseCase query = new QueryLoanAccountUseCase() {
            @Override public Result query(UUID ignored) { throw new UnsupportedOperationException(); }
            @Override public List<CustomerLoanAccountSummaryDto> queryOwnAccounts() {
                return List.of(new CustomerLoanAccountSummaryDto(
                        applicationId, UUID.randomUUID(), "LA-001", "UCL-001",
                        "UNSECURED_CONSUMER_LOAN", "UNSECURED", "ACTIVE",
                        LocalDateTime.of(2026, 8, 30, 9, 0),
                        BigDecimal.valueOf(10_000_000), BigDecimal.ZERO,
                        BigDecimal.valueOf(11_800_000), true
                ));
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomerLoanAccountController(query)).build();

        mvc.perform(get("/api/v1/loan-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanApplicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$[0].totalOutstanding").value(11800000))
                .andExpect(jsonPath("$[0].servicingActive").value(true))
                .andExpect(jsonPath("$[0].disbursementDestination").doesNotExist())
                .andExpect(jsonPath("$[0].transferReference").doesNotExist());
    }
}

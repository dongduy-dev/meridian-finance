package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffLoanApplicationControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
    );

    @Test
    void returnsRealPageEnvelopeAndForwardsFilters() throws Exception {
        QueryStaffLoanApplicationsUseCase useCase = new StubUseCase();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffLoanApplicationController(useCase)
        ).build();

        mockMvc.perform(get("/api/v1/staff/loan-applications")
                        .queryParam("productCode", "SALARY_ADVANCE")
                        .queryParam("status", "UNDER_REVIEW")
                        .queryParam("page", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].loanApplicationId")
                        .value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].requiredAction").doesNotExist());
    }

    @Test
    void caseJsonExposesOnlyPurposeLimitedReadinessAndLifecycleFacts() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffLoanApplicationController(new StubUseCase())
        ).build();

        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReadiness.active").value(true))
                .andExpect(jsonPath("$.customerReadiness.profileComplete").value(true))
                .andExpect(jsonPath("$.customerReadiness.email").doesNotExist())
                .andExpect(jsonPath("$.lifecycleHistory[0].fromStatus").value(nullValue()))
                .andExpect(jsonPath("$.lifecycleHistory[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.lifecycleHistory[0].actorType").value("SYSTEM"))
                .andExpect(jsonPath("$.lifecycleHistory[0].id").doesNotExist())
                .andExpect(jsonPath("$.lifecycleHistory[0].operationId").doesNotExist())
                .andExpect(jsonPath("$.lifecycleHistory[0].actorUserId").doesNotExist())
                .andExpect(jsonPath("$.lifecycleHistory[0].reason").doesNotExist())
                .andExpect(jsonPath("$.auditId").doesNotExist())
                .andExpect(jsonPath("$.externalReference").doesNotExist());
    }

    private static class StubUseCase implements QueryStaffLoanApplicationsUseCase {
        @Override
        public StaffLoanApplicationPageDto queryApplications(
                ProductCode productCode,
                LoanApplicationStatus status,
                int page,
                int size
        ) {
            return new StaffLoanApplicationPageDto(
                    page,
                    size,
                    21,
                    2,
                    List.of(item())
            );
        }

        @Override
        public StaffLoanApplicationCaseDto queryCase(UUID loanApplicationId) {
            return new StaffLoanApplicationCaseDto(
                    loanApplicationId,
                    "SA-20260902-000001",
                    "SALARY_ADVANCE",
                    "SALARY_BASED",
                    new BigDecimal("3000000.00"),
                    1,
                    "SUBMITTED",
                    LocalDateTime.of(2026, 9, 2, 8, 0),
                    new StaffLoanApplicationCaseDto.CustomerReadinessDto(
                            true,
                            true,
                            true,
                            "VERIFIED"
                    ),
                    List.of(new StaffLoanApplicationCaseDto.LifecycleItemDto(
                            null,
                            "SUBMITTED",
                            "SUBMIT_APPLICATION",
                            "SYSTEM",
                            LocalDateTime.of(2026, 9, 2, 8, 0)
                    ))
            );
        }

        private static StaffLoanApplicationPageDto.ItemDto item() {
            return new StaffLoanApplicationPageDto.ItemDto(
                    APPLICATION_ID,
                    "SA-20260902-000001",
                    "SALARY_ADVANCE",
                    "SALARY_BASED",
                    new BigDecimal("3000000.00"),
                    1,
                    "UNDER_REVIEW",
                    LocalDateTime.of(2026, 9, 2, 8, 0)
            );
        }
    }
}

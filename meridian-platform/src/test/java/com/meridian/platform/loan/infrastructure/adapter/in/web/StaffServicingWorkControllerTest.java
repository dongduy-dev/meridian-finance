package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffServicingWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffServicingWorkUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffServicingWorkControllerTest {

    @Test
    void returnsSafePagedServicingWorkAndAcceptsSupportedFilters() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffServicingWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get("/api/v1/staff/servicing-work")
                        .queryParam("productCode", "UNSECURED_CONSUMER_LOAN")
                        .queryParam("accountStatus", "OVERDUE")
                        .queryParam("page", "1")
                        .queryParam("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(26))
                .andExpect(jsonPath("$.items[0].accountStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.items[0].applicationNumber")
                        .value("UCL-20260910-000001"))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].externalPaymentReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].requestId").doesNotExist());
    }

    @Test
    void rejectsUnknownStatusAtTheHttpBoundary() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffServicingWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get("/api/v1/staff/servicing-work")
                        .queryParam("accountStatus", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    private static class StubUseCase implements QueryStaffServicingWorkUseCase {
        @Override
        public StaffServicingWorkPageDto queryWork(
                ProductCode productCode,
                LoanAccountStatus accountStatus,
                int page,
                int size
        ) {
            return new StaffServicingWorkPageDto(page, size, 26, 2, List.of(
                    new StaffServicingWorkPageDto.ItemDto(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "UCL-20260910-000001",
                            "LA-20260910-000001",
                            productCode.name(),
                            "UNSECURED",
                            accountStatus.name(),
                            LocalDateTime.of(2026, 9, 1, 10, 0),
                            new BigDecimal("1000.00"),
                            new BigDecimal("100.00"),
                            new BigDecimal("900.00"),
                            LocalDate.of(2026, 9, 10),
                            LocalDate.of(2026, 9, 9),
                            LocalDateTime.of(2026, 9, 9, 8, 0)
                    )
            ));
        }
    }
}

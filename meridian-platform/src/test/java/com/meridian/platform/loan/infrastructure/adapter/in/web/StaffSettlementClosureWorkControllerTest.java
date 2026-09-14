package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffClosureWorkPageDto;
import com.meridian.platform.loan.application.dto.StaffSettlementWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffClosureWorkUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffSettlementWorkUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffSettlementClosureWorkControllerTest {
    @Test
    void returnsPagedPiiMinimizedOperationalFacts() throws Exception {
        MockMvc mvc = mvc();

        mvc.perform(get("/api/v1/staff/settlement-work")
                        .queryParam("productCode", "UNSECURED_CONSUMER_LOAN")
                        .queryParam("page", "1").queryParam("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.items[0].totalOutstanding").value(900))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].requestId").doesNotExist())
                .andExpect(jsonPath("$.items[0].externalPaymentReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist());

        mvc.perform(get("/api/v1/staff/closure-work")
                        .queryParam("productCode", "SALARY_ADVANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountStatus").value("SETTLED"))
                .andExpect(jsonPath("$.items[0].payoffProvenance")
                        .value("APPROVED_SETTLEMENT"))
                .andExpect(jsonPath("$.items[0].closureId").doesNotExist())
                .andExpect(jsonPath("$.items[0].repaymentTransactionId").doesNotExist())
                .andExpect(jsonPath("$.items[0].exposureMovementId").doesNotExist());
    }

    @Test
    void rejectsAnUnknownProductAtTheTransportBoundary() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(get("/api/v1/staff/closure-work")
                        .queryParam("productCode", "NOT_A_PRODUCT"))
                .andExpect(status().isBadRequest());
    }

    private static MockMvc mvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(
                new StaffSettlementWorkController(new SettlementStub()),
                new StaffClosureWorkController(new ClosureStub())
        ).setValidator(validator).build();
    }

    private static class SettlementStub implements QueryStaffSettlementWorkUseCase {
        @Override
        public StaffSettlementWorkPageDto queryWork(ProductCode product, int page, int size) {
            return new StaffSettlementWorkPageDto(page, size, 26, 2, List.of(
                    new StaffSettlementWorkPageDto.ItemDto(
                            UUID.randomUUID(), UUID.randomUUID(), "UCL-20260914-1",
                            "LA-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                            product == null ? "UNSECURED_CONSUMER_LOAN" : product.name(),
                            "UNSECURED", "OVERDUE",
                            LocalDateTime.of(2026, 9, 1, 10, 0), money("100"),
                            money("900"), LocalDate.of(2026, 9, 14),
                            LocalDate.of(2026, 9, 13),
                            LocalDateTime.of(2026, 9, 13, 10, 0)
                    )
            ));
        }
    }

    private static class ClosureStub implements QueryStaffClosureWorkUseCase {
        @Override
        public StaffClosureWorkPageDto queryWork(ProductCode product, int page, int size) {
            return new StaffClosureWorkPageDto(page, size, 1, 1, List.of(
                    new StaffClosureWorkPageDto.ItemDto(
                            UUID.randomUUID(), UUID.randomUUID(), "SA-20260914-1",
                            "LA-BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                            product == null ? "SALARY_ADVANCE" : product.name(),
                            "SALARY_ADVANCE", "SETTLED",
                            LocalDateTime.of(2026, 9, 1, 10, 0), money("1000"),
                            BigDecimal.ZERO.setScale(2), LocalDate.of(2026, 9, 14),
                            LocalDate.of(2026, 9, 13),
                            LocalDateTime.of(2026, 9, 13, 10, 0),
                            "APPROVED_SETTLEMENT"
                    )
            ));
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

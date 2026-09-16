package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.port.in.ManageLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.QueryAdminLoanProductsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLoanProductControllerTest {
    @Test
    void exposesOnlyPurposeSpecificAdministrationProjection() throws Exception {
        QueryAdminLoanProductsUseCase query = mock(QueryAdminLoanProductsUseCase.class);
        when(query.findAll()).thenReturn(List.of(new AdminLoanProductDto(
                "COLLATERAL_LOAN", "SECURED", "Collateral Loan", "Secured lending.", false,
                new BigDecimal("5000000.00"), new BigDecimal("200000000.00")
        )));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AdminLoanProductController(query, mock(ManageLoanProductUseCase.class))
        ).build();

        mvc.perform(get("/api/v1/admin/loan-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("COLLATERAL_LOAN"))
                .andExpect(jsonPath("$[0].productType").value("SECURED"))
                .andExpect(jsonPath("$[0].name").value("Collateral Loan"))
                .andExpect(jsonPath("$[0].description").value("Secured lending."))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].minAmount").value(5000000.00))
                .andExpect(jsonPath("$[0].maxAmount").value(200000000.00))
                .andExpect(jsonPath("$[0].policy").doesNotExist())
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }
}

package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.port.in.ManageLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.QueryAdminLoanProductsUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.AdminLoanProductController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminLoanProductController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class LoanProductAdministrationSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryAdminLoanProductsUseCase query;
    @MockitoBean ManageLoanProductUseCase commands;

    @BeforeEach
    void setUp() {
        when(query.findAll()).thenReturn(List.of(product()));
        when(commands.updateLimits(eq("SALARY_ADVANCE"), any())).thenReturn(product());
        when(commands.changeActivation(eq("SALARY_ADVANCE"), any())).thenReturn(product());
    }

    @Test
    void exactLoanProductManageAllowsDiscoveryAndCommands() throws Exception {
        var actor = user("actor").authorities(new SimpleGrantedAuthority("loan:product:manage"));
        mockMvc.perform(get("/api/v1/admin/loan-products").with(actor)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/loan-products/SALARY_ADVANCE/limits")
                        .with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minAmount\":500000,\"maxAmount\":10000000}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/loan-products/SALARY_ADVANCE/activation")
                        .with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousAndLookalikeAuthoritiesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/loan-products")).andExpect(status().isUnauthorized());
        for (String denied : List.of(
                "loan:read", "loan:product", "loan:product:manage:all", "admin:config", "BACK_OFFICE_ADMIN"
        )) {
            mockMvc.perform(get("/api/v1/admin/loan-products")
                            .with(user("actor").authorities(new SimpleGrantedAuthority(denied))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void validatesNullableActivationAndNumericLimitShape() throws Exception {
        var actor = user("actor").authorities(new SimpleGrantedAuthority("loan:product:manage"));
        mockMvc.perform(put("/api/v1/admin/loan-products/SALARY_ADVANCE/activation")
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/loan-products/SALARY_ADVANCE/limits")
                        .with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minAmount\":-1,\"maxAmount\":10000000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/loan-products/SALARY_ADVANCE/limits")
                        .with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minAmount\":1.001,\"maxAmount\":10000000}"))
                .andExpect(status().isBadRequest());
    }

    private static AdminLoanProductDto product() {
        return new AdminLoanProductDto(
                "SALARY_ADVANCE", "SALARY_BASED", "Salary Advance", null, true,
                new BigDecimal("500000.00"), new BigDecimal("10000000.00")
        );
    }
}

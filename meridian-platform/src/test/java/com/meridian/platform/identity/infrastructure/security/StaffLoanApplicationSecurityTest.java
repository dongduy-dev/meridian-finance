package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffLoanApplicationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffLoanApplicationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffLoanApplicationSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffLoanApplicationsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(useCase.queryApplications(isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new StaffLoanApplicationPageDto(0, 20, 0, 0, List.of()));
        when(useCase.queryCase(any())).thenReturn(new StaffLoanApplicationCaseDto(
                APPLICATION_ID,
                "SA-20260902-000001",
                "SALARY_ADVANCE",
                "SALARY_BASED",
                new BigDecimal("3000000.00"),
                1,
                "SUBMITTED",
                LocalDateTime.of(2026, 9, 2, 8, 0),
                new StaffLoanApplicationCaseDto.CustomerReadinessDto(
                        true, true, true, "VERIFIED"
                ),
                List.of()
        ));
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/loan-applications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}",
                        APPLICATION_ID
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exactLoanReadAuthorityAllowsBothStaffReads() throws Exception {
        mockMvc.perform(get("/api/v1/staff/loan-applications").with(authority("loan:read")))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}",
                        APPLICATION_ID
                ).with(authority("loan:read")))
                .andExpect(status().isOk());
    }

    @Test
    void CustomerOwnAndUnrelatedAuthoritiesRemainForbidden() throws Exception {
        for (String denied : List.of(
                "loan:read:own",
                "loan:read:all",
                "loan:review",
                "approval:decide"
        )) {
            mockMvc.perform(get("/api/v1/staff/loan-applications")
                            .with(authority(denied)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(
                            "/api/v1/staff/loan-applications/{loanApplicationId}",
                            APPLICATION_ID
                    ).with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidPageArgumentsAndMaximumSizeAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/staff/loan-applications")
                        .queryParam("page", "-1")
                        .with(authority("loan:read")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff/loan-applications")
                        .queryParam("size", "0")
                        .with(authority("loan:read")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff/loan-applications")
                        .queryParam("size", "101")
                        .with(authority("loan:read")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff/loan-applications")
                        .queryParam("size", "100")
                        .with(authority("loan:read")))
                .andExpect(status().isOk());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
                    String authority
            ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

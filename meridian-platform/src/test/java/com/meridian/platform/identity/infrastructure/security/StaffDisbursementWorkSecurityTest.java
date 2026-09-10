package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryStaffDisbursementWorkUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffDisbursementWorkController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffDisbursementWorkController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffDisbursementWorkSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffDisbursementWorkUseCase useCase;

    @Test
    void exactDisbursePermissionProtectsQueueAndCase() throws Exception {
        mockMvc.perform(get("/api/v1/staff/disbursement-work")
                        .with(authority("loan:disburse")))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/disbursement",
                        APPLICATION_ID
                ).with(authority("loan:disburse")))
                .andExpect(status().isOk());
    }

    @Test
    void prefixesRolesAndUnrelatedPermissionsAreForbidden() throws Exception {
        for (String denied : List.of(
                "ACCOUNTING_OFFICER",
                "loan:read",
                "loan:disburse:all",
                "loan:disbursement:prepare"
        )) {
            mockMvc.perform(get("/api/v1/staff/disbursement-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/disbursement-work"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

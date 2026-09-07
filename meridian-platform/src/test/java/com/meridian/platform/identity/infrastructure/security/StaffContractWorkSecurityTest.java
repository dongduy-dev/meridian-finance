package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryStaffContractWorkUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffContractWorkController;
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

@WebMvcTest(controllers = StaffContractWorkController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffContractWorkSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffContractWorkUseCase useCase;

    @Test
    void exactContractReadPermissionProtectsQueueAndCase() throws Exception {
        mockMvc.perform(get("/api/v1/staff/contract-work")
                        .with(authority("loan:contract:read")))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/contract",
                        APPLICATION_ID
                ).with(authority("loan:contract:read")))
                .andExpect(status().isOk());
    }

    @Test
    void roleNamesPrefixesAndUnrelatedPermissionsAreForbidden() throws Exception {
        for (String denied : List.of(
                "ACCOUNTING_OFFICER",
                "loan:read",
                "loan:contract:read:all",
                "loan:contract:prepare"
        )) {
            mockMvc.perform(get("/api/v1/staff/contract-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/contract-work"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

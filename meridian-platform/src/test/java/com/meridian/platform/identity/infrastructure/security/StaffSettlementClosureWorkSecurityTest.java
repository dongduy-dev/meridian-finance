package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryStaffClosureWorkUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffSettlementWorkUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffClosureWorkController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffSettlementWorkController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StaffSettlementWorkController.class, StaffClosureWorkController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class, MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class})
class StaffSettlementClosureWorkSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffSettlementWorkUseCase settlementWork;
    @MockitoBean QueryStaffClosureWorkUseCase closureWork;

    @Test
    void exactPermissionsProtectPurposeSpecificQueues() throws Exception {
        mockMvc.perform(get("/api/v1/staff/settlement-work")
                        .with(authority("loan:settlement:approve")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/closure-work")
                        .with(authority("loan:account:close")))
                .andExpect(status().isOk());
    }

    @Test
    void nearbyPermissionsAndAnonymousCallersAreDenied() throws Exception {
        for (String denied : List.of("loan:read", "repayment:update", "approval:decide")) {
            mockMvc.perform(get("/api/v1/staff/settlement-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/staff/closure-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/staff/settlement-work"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/closure-work"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pagingConstraintsAreEnforcedAtTheWebBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/staff/settlement-work")
                        .queryParam("page", "-1")
                        .with(authority("loan:settlement:approve")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff/closure-work")
                        .queryParam("size", "101")
                        .with(authority("loan:account:close")))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
                    String authority
            ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

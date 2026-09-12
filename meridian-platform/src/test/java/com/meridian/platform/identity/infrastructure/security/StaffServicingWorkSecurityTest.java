package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryStaffServicingWorkUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffServicingWorkController;
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

@WebMvcTest(controllers = StaffServicingWorkController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffServicingWorkSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffServicingWorkUseCase useCase;

    @Test
    void exactLoanReadPermissionProtectsTheServicingQueue() throws Exception {
        mockMvc.perform(get("/api/v1/staff/servicing-work")
                        .with(authority("loan:read")))
                .andExpect(status().isOk());
    }

    @Test
    void roleNamesAndMutationOnlyPermissionsAreForbidden() throws Exception {
        for (String denied : List.of(
                "ACCOUNTING_OFFICER",
                "repayment:update",
                "loan:read:own",
                "loan:read:all"
        )) {
            mockMvc.perform(get("/api/v1/staff/servicing-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/servicing-work"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
                    String authority
            ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

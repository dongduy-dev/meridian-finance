package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountServicingProvenanceUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffLoanAccountServicingProvenanceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffLoanAccountServicingProvenanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class, MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class})
class StaffLoanAccountServicingProvenanceSecurityTest {
    private static final String PATH = "/api/v1/staff/loan-applications/"
            + UUID.fromString("11111111-1111-4111-8111-111111111111")
            + "/servicing-provenance";
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffLoanAccountServicingProvenanceUseCase useCase;

    @Test void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test void exactLoanReadAuthorityIsRequired() throws Exception {
        for (String denied : new String[]{"loan:read:own", "repayment:update",
                "loan:settlement:approve", "loan:account:close"}) {
            mockMvc.perform(get(PATH).with(user("actor").authorities(
                    new SimpleGrantedAuthority(denied)))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH).with(user("actor").authorities(
                new SimpleGrantedAuthority("loan:read")))).andExpect(status().isOk());
    }
}

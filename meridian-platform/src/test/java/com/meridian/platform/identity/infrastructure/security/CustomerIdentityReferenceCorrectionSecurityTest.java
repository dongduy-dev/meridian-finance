package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.customer.application.port.in.CorrectCustomerIdentityReferenceUseCase;
import com.meridian.platform.customer.infrastructure.adapter.in.web.CustomerIdentityReferenceCorrectionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerIdentityReferenceCorrectionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class})
class CustomerIdentityReferenceCorrectionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CorrectCustomerIdentityReferenceUseCase useCase;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    private static final String OWN = "/api/v1/customers/me/identity-reference";
    private static final String STAFF = "/api/v1/staff/customers/99999999-9999-9999-9999-999999999999/identity-reference";
    @Test void directRoutesRejectAnonymousAndUnrelatedAuthorities() throws Exception {
        for (String route : List.of(OWN, STAFF)) {
            mvc.perform(put(route).contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}")).andExpect(status().isUnauthorized());
            for (String permission : List.of("customer:read", "customer:identity:verify", "partner:manage", "identity:user:manage"))
                mvc.perform(put(route).with(user("actor").authorities(new SimpleGrantedAuthority(permission)))
                        .contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }
    @Test void exactPermissionsAuthorizeOnlyTheirRouteAndBodyCannotSelectCustomer() throws Exception {
        var own = user("customer").authorities(new SimpleGrantedAuthority("customer:profile:write:own"));
        var staff = user("staff").authorities(new SimpleGrantedAuthority("customer:intake:manage"));
        mvc.perform(put(OWN).with(own).contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"));
        mvc.perform(put(STAFF).with(staff).contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(STAFF).with(own).contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(OWN).with(staff).contentType("application/json").content("{\"identityReference\":\"FICTIONAL\"}"))
                .andExpect(status().isForbidden());
        clearInvocations(useCase);
        for (String body : List.of("{\"identityReference\":\"\"}"))
            mvc.perform(put(OWN).with(own).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
        mvc.perform(put(OWN).with(own).contentType("application/json")
                .content("{\"identityReference\":\"FICTIONAL\",\"customerId\":\"99999999-9999-9999-9999-999999999999\"}"))
                .andExpect(status().isOk());
        verify(useCase).correctOwn(new com.meridian.platform.customer.application.dto.CorrectIdentityReferenceRequest("FICTIONAL"));
        verifyNoMoreInteractions(useCase);
    }
}

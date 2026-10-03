package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffCustomerIdentityReferenceController;
import com.meridian.platform.loan.application.service.RevealStaffCustomerIdentityReferenceService;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffCustomerIdentityReferenceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class,
        SpringSecurityCurrentUserProvider.class, RevealStaffCustomerIdentityReferenceService.class})
class CustomerIdentityReferenceRevealSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean LoanApplicationRepository applications;
    @MockitoBean CustomerIdentityReferenceRevealPort customerReveal;
    private static final UUID ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final String PATH = "/api/v1/staff/loan-applications/" + ID + "/customer-identity-reference/reveal";
    private static final Set<String> PERMISSIONS = Set.of("loan:read", "customer:read", "customer:identity:reveal");
    private RequestPostProcessor actor(String type, UUID customerId, String role, Set<String> permissions) {
        var actor = new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", type, customerId, Set.of(role), permissions);
        return authentication(new UsernamePasswordAuthenticationToken(actor, null,
                permissions.stream().map(SimpleGrantedAuthority::new).toList()));
    }
    private RequestPostProcessor officer() { return actor("STAFF", null, "LOAN_OFFICER", PERMISSIONS); }

    @Test void explicitPostReturnsOnlyTheExactApplicationAndReferenceWithPrivateHeaders() throws Exception {
        var application = mock(LoanApplication.class);
        UUID customerId = UUID.randomUUID();
        when(application.id()).thenReturn(ID); when(application.customerId()).thenReturn(customerId);
        when(applications.findById(ID)).thenReturn(Optional.of(application));
        when(customerReveal.reveal(customerId, ID)).thenReturn(new CustomerIdentityReferenceRevealPort.Result("FICTIONAL-ID-8901"));
        mvc.perform(post(PATH).with(officer()).contentType("application/json")
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.loanApplicationId").value(ID.toString()))
                .andExpect(jsonPath("$.identityReference").value("FICTIONAL-ID-8901"));
        verify(customerReveal).reveal(customerId, ID);
    }

    @Test void anonymousAndEveryIncompleteAuthorityAreDeniedBeforeCustomerAccess() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        for (String role : List.of("APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN", "CUSTOM_ROLE"))
            mvc.perform(post(PATH).with(actor("STAFF", null, role, PERMISSIONS))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(actor("CUSTOMER", UUID.randomUUID(), "LOAN_OFFICER", PERMISSIONS))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(actor("STAFF", UUID.randomUUID(), "LOAN_OFFICER", PERMISSIONS))).andExpect(status().isForbidden());
        for (String missing : PERMISSIONS) {
            var permissions = new HashSet<>(PERMISSIONS); permissions.remove(missing);
            mvc.perform(post(PATH).with(actor("STAFF", null, "LOAN_OFFICER", permissions))).andExpect(status().isForbidden());
        }
        for (String alone : List.of("customer:identity:verify", "customer:read", "loan:read", "customer:identity:reveal"))
            mvc.perform(post(PATH).with(actor("STAFF", null, "LOAN_OFFICER", Set.of(alone)))).andExpect(status().isForbidden());
        verifyNoInteractions(applications, customerReveal);
    }

    @Test void invalidAndMissingApplicationsFailSafelyAndNoGenericCustomerRouteExists() throws Exception {
        mvc.perform(post(PATH.replace(ID.toString(), "invalid")).with(officer())).andExpect(status().isBadRequest());
        when(applications.findById(ID)).thenReturn(Optional.empty());
        mvc.perform(post(PATH).with(officer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));
        mvc.perform(post("/api/v1/staff/customers/" + UUID.randomUUID() + "/identity-reference/reveal").with(officer())).andExpect(status().isNotFound());
        verifyNoInteractions(customerReveal);
    }
}

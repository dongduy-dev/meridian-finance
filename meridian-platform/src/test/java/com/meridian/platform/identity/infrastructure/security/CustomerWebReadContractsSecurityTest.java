package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.document.application.port.in.QueryOwnDocumentChecklistUseCase;
import com.meridian.platform.document.infrastructure.adapter.in.web.CustomerDocumentChecklistController;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.CustomerLoanAccountController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanApplicationController;
import com.meridian.platform.partner.application.port.in.QueryPartnerVerificationOptionsUseCase;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerVerificationOptionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PartnerVerificationOptionController.class,
        LoanApplicationController.class,
        CustomerLoanAccountController.class,
        CustomerDocumentChecklistController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class CustomerWebReadContractsSecurityTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryPartnerVerificationOptionsUseCase partners;
    @MockitoBean QueryLoanApplicationUseCase applications;
    @MockitoBean QueryLoanAccountUseCase accounts;
    @MockitoBean QueryOwnDocumentChecklistUseCase documents;

    @BeforeEach
    void setUp() {
        when(partners.query()).thenReturn(List.of());
        when(applications.queryOwnApplications()).thenReturn(List.of());
        when(accounts.queryOwnAccounts()).thenReturn(List.of());
        when(documents.query(any())).thenReturn(null);
    }

    @Test
    void customerReadContractsRejectAnonymousRequests() throws Exception {
        for (String path : List.of(
                "/api/v1/partner-companies/verification-options",
                "/api/v1/loan-applications",
                "/api/v1/loan-accounts",
                "/api/v1/loan-applications/" + APPLICATION_ID + "/documents"
        )) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void eachContractRequiresItsExistingNarrowPermission() throws Exception {
        expectAllowed("/api/v1/partner-companies/verification-options",
                "partner:employee:verify:own");
        expectAllowed("/api/v1/loan-applications", "loan:read:own");
        expectAllowed("/api/v1/loan-accounts", "loan:read:own");
        expectAllowed("/api/v1/loan-applications/" + APPLICATION_ID + "/documents",
                "document:read:own");
    }

    private void expectAllowed(String path, String permission) throws Exception {
        mockMvc.perform(get(path).with(user("customer").authorities(
                        new SimpleGrantedAuthority(permission))))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).with(user("customer").authorities(
                        new SimpleGrantedAuthority("customer:profile:read:own"))))
                .andExpect(status().isForbidden());
    }
}

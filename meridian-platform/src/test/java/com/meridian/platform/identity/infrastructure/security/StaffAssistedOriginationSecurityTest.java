package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.customer.application.port.in.StaffCustomerIntakeUseCase;
import com.meridian.platform.customer.infrastructure.adapter.in.web.StaffCustomerIntakeController;
import com.meridian.platform.document.application.port.in.ManageIntakeEvidenceUseCase;
import com.meridian.platform.document.infrastructure.adapter.in.web.StaffIntakeEvidenceController;
import com.meridian.platform.loan.application.port.in.ManageAssistedOriginationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.AssistedOriginationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        StaffCustomerIntakeController.class,
        AssistedOriginationController.class,
        StaffIntakeEvidenceController.class
})
@Import({
        SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class
})
class StaffAssistedOriginationSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean StaffCustomerIntakeUseCase customers;
    @MockitoBean ManageAssistedOriginationUseCase cases;
    @MockitoBean ManageIntakeEvidenceUseCase evidence;

    @Test
    void exactPermissionsProtectEachBoundary() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(cases.findCases(any())).thenReturn(List.of());
        when(evidence.findEvidence(caseId)).thenReturn(List.of());
        var customerRead = user("staff").authorities(new SimpleGrantedAuthority("customer:read"));
        var originate = user("staff").authorities(new SimpleGrantedAuthority("loan:originate:staff"));
        var upload = user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"));

        mockMvc.perform(post("/api/v1/staff/customers/search").with(customerRead)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerNumber\":\"CUS-000000001\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/assisted-originations").with(originate))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/assisted-originations/{id}/evidence", caseId).with(upload))
                .andExpect(status().isOk());
    }

    @Test
    void customerAndCorrectionUploadAuthoritiesDoNotGrantIntakeAccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/staff/assisted-originations")
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/staff/assisted-originations/{id}/evidence/CUSTOMER_IDENTITY/versions", caseId)
                        .file(new MockMultipartFile("file", "identity.pdf", "application/pdf", "%PDF".getBytes()))
                        .param("uploadRequestId", UUID.randomUUID().toString())
                        .with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:staff"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rawIdentitySearchUsesJsonBodyContract() throws Exception {
        mockMvc.perform(post("/api/v1/staff/customers/search")
                        .with(user("staff").authorities(new SimpleGrantedAuthority("customer:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityReference\":\"012345678901\"}"))
                .andExpect(status().isOk());
    }
}

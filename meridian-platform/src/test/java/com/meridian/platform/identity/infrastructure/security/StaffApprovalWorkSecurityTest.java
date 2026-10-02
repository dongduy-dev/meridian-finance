package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.approval.application.port.in.QueryStaffApprovalWorkUseCase;
import com.meridian.platform.approval.infrastructure.adapter.in.web.StaffApprovalWorkController;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = StaffApprovalWorkController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffApprovalWorkSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffApprovalWorkUseCase useCase;

    @Test
    void exactPurposePermissionsProtectEachRead() throws Exception {
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/recommendation", APPLICATION_ID)
                        .with(authority("approval:recommend")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/decision", APPLICATION_ID)
                        .with(authority("approval:decide")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/approval-work").with(authority("approval:decide")))
                .andExpect(status().isOk());
    }

    @Test
    void decisionAuthorityAloneReturnsOnlyCustomerNumberAndName() throws Exception {
        org.mockito.Mockito.when(useCase.queryDecisionCase(APPLICATION_ID)).thenReturn(
                new com.meridian.platform.approval.application.dto.StaffDecisionCaseDto(
                        APPLICATION_ID, "UCL-1", "UNSECURED_CONSUMER_LOAN", "UNSECURED",
                        java.math.BigDecimal.TEN, 6, "APPROVAL_PENDING", java.time.LocalDateTime.of(2026, 9, 6, 8, 0),
                        new com.meridian.platform.approval.application.dto.StaffDecisionCaseDto.CustomerDto("CUS-001", "Ari Customer"),
                        null, null, true, true, null, List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/decision", APPLICATION_ID)
                        .with(authority("approval:decide")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.customerNumber").value("CUS-001"))
                .andExpect(jsonPath("$.customer.fullName").value("Ari Customer"))
                .andExpect(jsonPath("$.customer.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.customer.identityReference").doesNotExist())
                .andExpect(jsonPath("$.customer.customerId").doesNotExist())
                .andExpect(jsonPath("$.customer.userId").doesNotExist())
                .andExpect(jsonPath("$.customer.email").doesNotExist())
                .andExpect(jsonPath("$.customer.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void roleNamesPrefixesAndUnrelatedPermissionsAreForbidden() throws Exception {
        for (String denied : List.of("APPROVER", "loan:read", "customer:read", "partner:read", "admin:config", "approval:decide:all", "approval:recommend:all")) {
            mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/decision", APPLICATION_ID)
                            .with(authority(denied)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/staff/approval-work").with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/recommendation", APPLICATION_ID)
                        .with(authority("approval:decide")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/approval-work")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

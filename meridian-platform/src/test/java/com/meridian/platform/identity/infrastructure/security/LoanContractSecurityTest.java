package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.QueryCurrentLoanContractUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanContractController;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanContractController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanContractMapper.class
})
class LoanContractSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean PrepareLoanContractUseCase prepareLoanContractUseCase;
    @MockitoBean QueryCurrentLoanContractUseCase queryCurrentLoanContractUseCase;
    @MockitoBean AcknowledgeLoanContractUseCase acknowledgeLoanContractUseCase;
    @MockitoBean QueryContractReadinessUseCase queryContractReadinessUseCase;
    @MockitoBean ConfirmContractReadinessUseCase confirmContractReadinessUseCase;

    @BeforeEach
    void setUp() {
        when(queryCurrentLoanContractUseCase.findCurrent(any()))
                .thenReturn(Optional.of(LoanContractTestData.prepared()));
        when(prepareLoanContractUseCase.prepare(any())).thenReturn(LoanContractTestData.prepared());
        when(acknowledgeLoanContractUseCase.acknowledge(any())).thenReturn(LoanContractTestData.acknowledged());
        when(confirmContractReadinessUseCase.confirm(any())).thenReturn(LoanContractTestData.ready());
        when(queryContractReadinessUseCase.query(any(), any()))
                .thenReturn(new QueryContractReadinessUseCase.Snapshot(
                        LoanContractTestData.APPLICATION_ID,
                        LoanContractTestData.CONTRACT_ID,
                        1,
                        true,
                        List.of()
                ));
    }

    @Test
    void rejectsUnauthenticatedContractAccess() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current",
                        LoanContractTestData.APPLICATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsCustomerOwnedReadAndAcknowledgmentPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:read:own")))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/acknowledgment",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:contract:acknowledge:own"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acknowledgmentRequestId": "%s",
                                  "expectedContractVersion": 1
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void allowsAccountingPermissionsAcrossTheStaffWorkflow() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:contract:prepare"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preparationRequestId": "%s",
                                  "expectedCurrentContractVersion": 0
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:contract:read")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current/readiness",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:contract:read")))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:disbursement:prepare"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationRequestId": "%s",
                                  "expectedContractVersion": 1
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void deniesCustomerAndOtherStaffWithoutTheExactPermission() throws Exception {
        var customer = user("customer").authorities(new SimpleGrantedAuthority("loan:read:own"));
        var loanOfficer = user("loan-officer").authorities(new SimpleGrantedAuthority("loan:review"));
        var approver = user("approver").authorities(new SimpleGrantedAuthority("approval:decide"));
        var backOffice = user("back-office").authorities(new SimpleGrantedAuthority("admin:config"));

        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts",
                        LoanContractTestData.APPLICATION_ID)
                        .with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPreparation()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                        LoanContractTestData.APPLICATION_ID)
                        .with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmation()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts",
                        LoanContractTestData.APPLICATION_ID)
                        .with(loanOfficer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPreparation()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current/readiness",
                        LoanContractTestData.APPLICATION_ID)
                        .with(approver))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                        LoanContractTestData.APPLICATION_ID)
                        .with(backOffice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmation()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedIdentifiersAndBodiesBeforeInvokingTheWorkflow() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/not-a-uuid/contracts/current")
                        .with(authority("loan:read:own")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts",
                        LoanContractTestData.APPLICATION_ID)
                        .with(authority("loan:contract:prepare"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preparationRequestId": null,
                                  "expectedCurrentContractVersion": -1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String authority
    ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static String validPreparation() {
        return """
                {
                  "preparationRequestId": "%s",
                  "expectedCurrentContractVersion": 0
                }
                """.formatted(UUID.randomUUID());
    }

    private static String validConfirmation() {
        return """
                {
                  "confirmationRequestId": "%s",
                  "expectedContractVersion": 1
                }
                """.formatted(UUID.randomUUID());
    }
}

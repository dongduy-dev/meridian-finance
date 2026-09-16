package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
import com.meridian.platform.partner.application.port.in.ManagePartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeImportBatchUseCase;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerCompanyController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeImportBatchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PartnerCompanyController.class, PartnerEmployeeImportBatchController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class PartnerAdministrationSecurityTest {

    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;
    @MockitoBean QueryPartnerEmployeeImportBatchUseCase queryPartnerEmployeeImportBatchUseCase;
    @MockitoBean ManagePartnerCompanyUseCase managePartnerCompanyUseCase;
    @MockitoBean ImportPartnerEmployeesUseCase importPartnerEmployeesUseCase;

    @BeforeEach
    void setUp() {
        when(managePartnerCompanyUseCase.create(any())).thenReturn(new PartnerCompanyDto(
                COMPANY_ID, "MERIDIAN", "Meridian Partner", "ACTIVE", new BigDecimal("20000000.00")
        ));
        when(importPartnerEmployeesUseCase.importEmployees(any(), any())).thenReturn(
                new PartnerEmployeeImportResultDto(
                        UUID.randomUUID(), COMPANY_ID, "2026-09", "COMPLETED", 1, 0, List.of()
                )
        );
    }

    @Test
    void exactPartnerManageAllowsCompanyAndImportCommands() throws Exception {
        mockMvc.perform(post("/api/v1/partner-companies")
                        .with(authority("partner:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/partner-companies/{companyId}/employee-import-batches", COMPANY_ID)
                        .with(authority("partner:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void readOnlyAndLookalikeAuthoritiesCannotMutate() throws Exception {
        for (String denied : List.of("partner:read", "partner:manage:all", "BACK_OFFICE_ADMIN")) {
            mockMvc.perform(post("/api/v1/partner-companies")
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(companyRequest()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/partner-companies/{companyId}/employee-import-batches", COMPANY_ID)
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(importRequest()))
                    .andExpect(status().isForbidden());
        }
    }

    private static String companyRequest() {
        return """
                {"companyCode":"MERIDIAN","name":"Meridian Partner","status":"ACTIVE","salaryAdvancePolicyLimit":20000000}
                """;
    }

    private static String importRequest() {
        return """
                {"requestId":"22222222-2222-4222-8222-222222222222","effectiveMonth":"2026-09","rows":[
                  {"employeeCode":"EMP-1","identityReference":"ID-1","salaryAmount":10000000,
                   "salaryAdvanceLimit":4000000,"employmentStatus":"ACTIVE","active":true}
                ]}
                """;
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

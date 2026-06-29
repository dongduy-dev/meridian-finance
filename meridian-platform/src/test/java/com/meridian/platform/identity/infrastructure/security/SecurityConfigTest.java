package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanProductController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.SalaryAdvanceLoanApplicationController;
import com.meridian.platform.partner.application.port.in.QueryPartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeImportBatchUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerCompanyController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeImportBatchController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeVerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        LoanProductController.class,
        SalaryAdvanceLoanApplicationController.class,
        PartnerCompanyController.class,
        PartnerEmployeeController.class,
        PartnerEmployeeImportBatchController.class,
        PartnerEmployeeVerificationController.class
})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final UUID PARTNER_COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryLoanProductUseCase queryLoanProductUseCase;

    @MockitoBean
    private StartSalaryAdvanceApplicationUseCase startSalaryAdvanceApplicationUseCase;

    @MockitoBean
    private QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;

    @MockitoBean
    private QueryPartnerEmployeeUseCase queryPartnerEmployeeUseCase;

    @MockitoBean
    private QueryPartnerEmployeeImportBatchUseCase queryPartnerEmployeeImportBatchUseCase;

    @MockitoBean
    private VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase;

    @Test
    void keepsLoanProductCatalogPublic() throws Exception {
        when(queryLoanProductUseCase.findActiveLoanProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/loan-products"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsAnonymousAccessToSensitivePartnerAndSalaryAdvanceEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employees", PARTNER_COMPANY_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches", PARTNER_COMPANY_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/partner-companies/{partnerCompanyId}/employee-verifications", PARTNER_COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/salary-advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedAccessToProtectedPartnerEmployeeEndpoint() throws Exception {
        when(queryPartnerEmployeeUseCase.getPartnerEmployeesByCompanyId(PARTNER_COMPANY_ID, true))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employees", PARTNER_COMPANY_ID)
                        .param("activeOnly", "true")
                        .with(user("back-office-admin")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}

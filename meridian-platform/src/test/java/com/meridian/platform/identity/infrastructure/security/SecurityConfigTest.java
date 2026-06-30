package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.infrastructure.adapter.in.web.ReviewRecommendationController;
import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.infrastructure.adapter.in.web.AuthController;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanApplicationReviewController;
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
import com.meridian.platform.shared.infrastructure.web.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        HealthController.class,
        AuthController.class,
        LoanProductController.class,
        SalaryAdvanceLoanApplicationController.class,
        LoanApplicationReviewController.class,
        ReviewRecommendationController.class,
        PartnerCompanyController.class,
        PartnerEmployeeController.class,
        PartnerEmployeeImportBatchController.class,
        PartnerEmployeeVerificationController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class SecurityConfigTest {

    private static final UUID PARTNER_COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationUseCase authenticationUseCase;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private QueryLoanProductUseCase queryLoanProductUseCase;

    @MockitoBean
    private StartSalaryAdvanceApplicationUseCase startSalaryAdvanceApplicationUseCase;

    @MockitoBean
    private StartLoanApplicationReviewUseCase startLoanApplicationReviewUseCase;

    @MockitoBean
    private SubmitReviewRecommendationUseCase submitReviewRecommendationUseCase;

    @MockitoBean
    private QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;

    @MockitoBean
    private QueryPartnerEmployeeUseCase queryPartnerEmployeeUseCase;

    @MockitoBean
    private QueryPartnerEmployeeImportBatchUseCase queryPartnerEmployeeImportBatchUseCase;

    @MockitoBean
    private VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase;

    @Test
    void keepsVersionedHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.app").value("Meridian Platform"));
    }

    @Test
    void doesNotExposeLegacyHealthAlias() throws Exception {
        mockMvc.perform(get("/api/health")
                        .with(user("authenticated-user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsLoginAndLoanProductCatalogPublic() throws Exception {
        when(queryLoanProductUseCase.findActiveLoanProducts()).thenReturn(List.of());
        when(authenticationUseCase.login(any())).thenReturn(new AuthResponse(
                "Bearer",
                "access-token",
                Instant.now().plusSeconds(3600),
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "customer.demo@meridian.local",
                "CUSTOMER",
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                Set.of("CUSTOMER"),
                Set.of("loan:submit")
        ));

        mockMvc.perform(get("/api/v1/loan-products"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer.demo@meridian.local",
                                  "password": "local-demo-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void rejectsAnonymousAccessToSensitivePartnerSalaryAdvanceAndReviewEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

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

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedUsersWithoutRequiredPermission() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employees", PARTNER_COMPANY_ID)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "RECOMMEND_APPROVAL"
                                }
                                """)
                        .with(user("reviewer").authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void allowsStaffWithPartnerReadPermissionToProtectedPartnerEmployeeEndpoint() throws Exception {
        when(queryPartnerEmployeeUseCase.getPartnerEmployeesByCompanyId(PARTNER_COMPANY_ID, true))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/partner-companies/{partnerCompanyId}/employees", PARTNER_COMPANY_ID)
                        .param("activeOnly", "true")
                        .with(user("back-office-admin")
                                .authorities(new SimpleGrantedAuthority("partner:read"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void allowsCustomerWithLoanSubmitPermissionToCreateSalaryAdvanceApplication() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/salary-advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerPartnerEmployeeLinkId": "%s",
                                  "requestedAmount": 3000000.00,
                                  "requestedTermMonths": 1
                                }
                                """.formatted(LINK_ID))
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isCreated());
    }

    @Test
    void allowsLoanOfficerWithLoanReviewPermissionToStartReview() throws Exception {
        when(startLoanApplicationReviewUseCase.startReview(LOAN_APPLICATION_ID))
                .thenReturn(new LoanApplicationReviewDto(LOAN_APPLICATION_ID, "UNDER_REVIEW"));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID)
                        .with(user("loan-officer")
                                .authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void allowsLoanOfficerWithApprovalRecommendPermissionToRecommend() throws Exception {
        UUID recommendationId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID loanOfficerUserId = UUID.fromString("00000000-0000-0000-0000-000000000302");
        when(submitReviewRecommendationUseCase.submitReviewRecommendation(any(), any()))
                .thenReturn(new ReviewRecommendationDto(
                        recommendationId,
                        LOAN_APPLICATION_ID,
                        loanOfficerUserId,
                        "RECOMMEND_APPROVAL",
                        null,
                        null,
                        LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "RECOMMEND_APPROVAL"
                                }
                                """)
                        .with(user("loan-officer")
                                .authorities(new SimpleGrantedAuthority("approval:recommend"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendationId").value(recommendationId.toString()))
                .andExpect(jsonPath("$.action").value("RECOMMEND_APPROVAL"));
    }
}

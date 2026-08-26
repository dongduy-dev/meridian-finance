package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.infrastructure.adapter.in.web.ApprovalDecisionController;
import com.meridian.platform.approval.infrastructure.adapter.in.web.ReviewRecommendationController;
import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.CustomerProfileDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerBankAccountsUseCase;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerUseCase;
import com.meridian.platform.customer.application.port.in.UpdateOwnCustomerProfileUseCase;
import com.meridian.platform.customer.infrastructure.adapter.in.web.CustomerBankAccountController;
import com.meridian.platform.customer.infrastructure.adapter.in.web.CustomerProfileController;
import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.infrastructure.adapter.in.web.AuthController;
import com.meridian.platform.identity.infrastructure.adapter.in.web.RefreshTokenCookieService;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.ApprovedOfferController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.CollateralLoanApplicationController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.CollateralLoanVerificationController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanApplicationReviewController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanProductController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.SalaryAdvanceLoanApplicationController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.UnsecuredConsumerLoanApplicationController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.UnsecuredConsumerLoanVerificationController;
import com.meridian.platform.partner.application.port.in.QueryPartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeImportBatchUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerCompanyController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeImportBatchController;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEmployeeVerificationController;
import com.meridian.platform.shared.infrastructure.web.HealthController;
import com.meridian.platform.shared.infrastructure.web.OpenApiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CustomerBankAccountController.class,
        CustomerProfileController.class,
        HealthController.class,
        AuthController.class,
        LoanProductController.class,
        SalaryAdvanceLoanApplicationController.class,
        CollateralLoanApplicationController.class,
        CollateralLoanVerificationController.class,
        UnsecuredConsumerLoanApplicationController.class,
        UnsecuredConsumerLoanVerificationController.class,
        LoanApplicationReviewController.class,
        ReviewRecommendationController.class,
        ApprovalDecisionController.class,
        ApprovedOfferController.class,
        PartnerCompanyController.class,
        PartnerEmployeeController.class,
        PartnerEmployeeImportBatchController.class,
        PartnerEmployeeVerificationController.class
})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        RefreshTokenCookieService.class,
        OpenApiConfig.class
})
@TestPropertySource(properties = "meridian.web.cors.allowed-origins=https://frontend.meridian.test")
class SecurityConfigTest {

    private static final String CONFIGURED_FRONTEND_ORIGIN = "https://frontend.meridian.test";
    private static final String DISALLOWED_FRONTEND_ORIGIN = "https://untrusted.example";
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
    private com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.LogoutUseCase logoutUseCase;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase registerCustomerUseCase;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase requestEmailVerificationUseCase;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @MockitoBean
    private QueryLoanProductUseCase queryLoanProductUseCase;

    @MockitoBean
    private QueryApprovedOfferUseCase queryApprovedOfferUseCase;

    @MockitoBean
    private RespondToApprovedOfferUseCase respondToApprovedOfferUseCase;

    @MockitoBean
    private StartSalaryAdvanceApplicationUseCase startSalaryAdvanceApplicationUseCase;

    @MockitoBean
    private StartUnsecuredConsumerLoanApplicationUseCase startUnsecuredConsumerLoanApplicationUseCase;

    @MockitoBean
    private StartCollateralLoanApplicationUseCase startCollateralLoanApplicationUseCase;

    @MockitoBean
    private ManageUnsecuredConsumerLoanVerificationUseCase manageUnsecuredConsumerLoanVerificationUseCase;

    @MockitoBean
    private ManageCollateralLoanVerificationUseCase manageCollateralLoanVerificationUseCase;

    @MockitoBean
    private StartLoanApplicationReviewUseCase startLoanApplicationReviewUseCase;

    @MockitoBean
    private SubmitReviewRecommendationUseCase submitReviewRecommendationUseCase;

    @MockitoBean
    private SubmitApprovalDecisionUseCase submitApprovalDecisionUseCase;

    @MockitoBean
    private QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;

    @MockitoBean
    private QueryPartnerEmployeeUseCase queryPartnerEmployeeUseCase;

    @MockitoBean
    private QueryPartnerEmployeeImportBatchUseCase queryPartnerEmployeeImportBatchUseCase;

    @MockitoBean
    private VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase;

    @MockitoBean
    private QueryOwnCustomerUseCase queryOwnCustomerUseCase;

    @MockitoBean
    private UpdateOwnCustomerProfileUseCase updateOwnCustomerProfileUseCase;

    @MockitoBean
    private QueryOwnCustomerBankAccountsUseCase queryOwnCustomerBankAccountsUseCase;

    @MockitoBean
    private ManageOwnCustomerBankAccountUseCase manageOwnCustomerBankAccountUseCase;

    @Test
    void keepsOpenApiAndSwaggerUiPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void generatesMeridianMetadataAndBearerJwtSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Meridian Lending Platform API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void allowsCorsPreflightFromConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/customers/me")
                        .header(HttpHeaders.ORIGIN, CONFIGURED_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type, X-Request-ID"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,OPTIONS"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "Authorization, Content-Type, X-Request-ID"
                ))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void exposesRequestIdAndRetryAfterToConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header(HttpHeaders.ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Request-ID, Retry-After"
                ));
    }

    @Test
    void deniesCorsAccessToDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/customers/me")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

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
        when(authenticationUseCase.login(any())).thenReturn(authenticationResult("access-token", "refresh-token"));

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
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("MERIDIAN_REFRESH_TOKEN=refresh-token"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
                                org.hamcrest.Matchers.containsString("Max-Age=604800"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict")
                        )
                ));
    }

    @Test
    void keepsRefreshPublicAtBearerLayerAndAllowsCredentialedCors() throws Exception {
        when(authenticationUseCase.refresh("refresh-token"))
                .thenReturn(authenticationResult("rotated-access-token", "rotated-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("MERIDIAN_REFRESH_TOKEN", "refresh-token"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-access-token")
                        .header(HttpHeaders.ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.accessToken").value("rotated-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void keepsRegistrationEmailVerificationAndPasswordResetPublicAtTheBearerLayer() throws Exception {
        when(registerCustomerUseCase.register(any())).thenReturn(
                com.meridian.platform.identity.application.dto.CustomerRegistrationResponse.verificationRequired()
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "registration-password",
                                  "displayName": "Customer Name"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/email-verification/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "customer@example.com"}
                                """))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "opaque-verification-token"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "customer@example.com"}
                                """))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "opaque-password-reset-token",
                                  "newPassword": "new-password-value"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                ));
    }

    @Test
    void rateLimitsLoginAtTheMvcBoundaryAndExposesRetryAfterToCorsClients() throws Exception {
        when(authenticationUseCase.login(any())).thenReturn(authenticationResult("access-token", "refresh-token"));

        for (int requestNumber = 0; requestNumber < 10; requestNumber++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.50");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "customer.demo@meridian.local",
                                      "password": "local-demo-password"
                                    }
                                    """))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.50");
                            return request;
                        })
                        .header(HttpHeaders.ORIGIN, CONFIGURED_FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer.demo@meridian.local",
                                  "password": "local-demo-password"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_FRONTEND_ORIGIN))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Request-ID, Retry-After"
                ))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests."));
    }

    @Test
    void keepsLogoutPublicAndClearsTheRefreshCookieDespiteMalformedBearer() throws Exception {
        when(jwtTokenService.parseAccessTokenDetails("malformed-access-token"))
                .thenThrow(new JwtAuthenticationException("INVALID_TOKEN", "Invalid token."));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("MERIDIAN_REFRESH_TOKEN", "refresh-token"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-access-token"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("MERIDIAN_REFRESH_TOKEN="),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
                                org.hamcrest.Matchers.containsString("Max-Age=0"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict")
                        )
                ));
    }

    @Test
    void expiredBearerDoesNotPreventPresentedRefreshCredentialLogout() throws Exception {
        when(jwtTokenService.parseAccessTokenDetails("expired-access-token"))
                .thenThrow(new JwtAuthenticationException("TOKEN_EXPIRED", "Token expired."));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("MERIDIAN_REFRESH_TOKEN", "refresh-token"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-access-token"))
                .andExpect(status().isNoContent());

        verify(logoutUseCase).logout(argThat(command ->
                command.refreshToken().filter("refresh-token"::equals).isPresent()
                        && command.accessToken().isEmpty()
        ));
    }

    @Test
    void rejectsAnonymousAccessToSensitivePartnerSalaryAdvanceReviewAndDecisionEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(put("/api/v1/customers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/customers/me/bank-accounts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/customers/me/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

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

        mockMvc.perform(post("/api/v1/loan-applications/unsecured-consumer-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review/start", LOAN_APPLICATION_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/review-recommendations", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/loan-applications/{loanApplicationId}/approved-offer", LOAN_APPLICATION_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept", LOAN_APPLICATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedUsersWithoutRequiredPermission() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me")
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/customers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Customer Demo",
                                  "identityReference": "IDREF-MER-001",
                                  "phoneNumber": "0901234567",
                                  "residentialAddress": "1 Meridian Street",
                                  "employmentStatus": "SALARIED",
                                  "employerName": "Meridian Partner Co",
                                  "termsConsentAccepted": true,
                                  "dataProcessingConsentAccepted": true
                                }
                                """)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("customer:profile:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/customers/me/bank-accounts")
                        .with(user("customer").authorities(new SimpleGrantedAuthority("customer:profile:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/customers/me/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "VCB",
                                  "bankNameSnapshot": "Vietcombank",
                                  "accountHolderName": "Customer Demo",
                                  "accountNumber": "1234567890"
                                }
                                """)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("customer:bank-account:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

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

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE"
                                }
                                """)
                        .with(user("loan-officer").authorities(new SimpleGrantedAuthority("approval:recommend"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/loan-applications/{loanApplicationId}/approved-offer", LOAN_APPLICATION_ID)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept", LOAN_APPLICATION_ID)
                        .with(user("customer").authorities(new SimpleGrantedAuthority("loan:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }


    @Test
    void allowsCustomerWithProfilePermissionsToReadAndUpdateOwnProfile() throws Exception {
        when(queryOwnCustomerUseCase.getOwnCustomer()).thenReturn(customerDto());
        when(updateOwnCustomerProfileUseCase.updateOwnProfile(any(UpdateCustomerProfileRequest.class)))
                .thenReturn(customerDto());

        mockMvc.perform(get("/api/v1/customers/me")
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("customer:profile:read:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNumber").value("CUS-000000001"))
                .andExpect(jsonPath("$.profile.fullName").value("Customer Demo"))
                .andExpect(jsonPath("$.profile.identityReference").doesNotExist());

        mockMvc.perform(put("/api/v1/customers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Customer Demo",
                                  "identityReference": "IDREF-MER-001",
                                  "phoneNumber": "0901234567",
                                  "residentialAddress": "1 Meridian Street",
                                  "employmentStatus": "SALARIED",
                                  "employerName": "Meridian Partner Co",
                                  "termsConsentAccepted": true,
                                  "dataProcessingConsentAccepted": true
                                }
                                """)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("customer:profile:write:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.identityReference").doesNotExist());
    }

    @Test
    void allowsCustomerWithBankAccountPermissionsToReadAndAddMaskedAccounts() throws Exception {
        when(queryOwnCustomerBankAccountsUseCase.getOwnBankAccounts()).thenReturn(List.of(bankAccountDto()));
        when(manageOwnCustomerBankAccountUseCase.addBankAccount(any(AddCustomerBankAccountRequest.class)))
                .thenReturn(bankAccountDto());

        mockMvc.perform(get("/api/v1/customers/me/bank-accounts")
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("customer:bank-account:read:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("****7890"))
                .andExpect(jsonPath("$[0].accountNumber").doesNotExist())
                .andExpect(jsonPath("$[0].accountNumberCiphertext").doesNotExist())
                .andExpect(jsonPath("$[0].accountNumberFingerprint").doesNotExist());

        mockMvc.perform(post("/api/v1/customers/me/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "VCB",
                                  "bankNameSnapshot": "Vietcombank",
                                  "accountHolderName": "Customer Demo",
                                  "accountNumber": "1234567890"
                                }
                                """)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("customer:bank-account:write:own"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****7890"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());
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
    void allowsCustomerWithLoanSubmitPermissionToCreateUnsecuredConsumerLoanApplication() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/unsecured-consumer-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedAmount": 5000000,
                                  "requestedTermMonths": 6
                                }
                                """)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isCreated());
    }

    @Test
    void enforcesLoanSubmitPermissionForCollateralLoanSubmission() throws Exception {
        String request = """
                {
                  "requestedAmount": 25000000,
                  "requestedTermMonths": 12,
                  "collateral": {
                    "type": "MOTORBIKE",
                    "description": "2024 Honda motorbike",
                    "estimatedValue": 35000000,
                    "ownershipStatus": "Customer-provided ownership statement",
                    "conditionNote": "Normal used condition"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/loan-applications/collateral-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void rejectsUnsecuredConsumerLoanSubmissionWithoutLoanSubmitPermission() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/unsecured-consumer-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedAmount": 5000000,
                                  "requestedTermMonths": 6
                                }
                                """)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:read:own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
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
    void allowsOnlyLoanReviewAuthorityForUclManualVerificationCommands() throws Exception {
        when(manageUnsecuredConsumerLoanVerificationUseCase.startManualVerification(LOAN_APPLICATION_ID))
                .thenReturn(new UnsecuredConsumerLoanVerificationDto(
                        LOAN_APPLICATION_ID, "VERIFICATION_PENDING", "PENDING_MANUAL_REVIEW", null));
        when(manageUnsecuredConsumerLoanVerificationUseCase.completeManualVerification(any(), any()))
                .thenReturn(new UnsecuredConsumerLoanVerificationDto(
                        LOAN_APPLICATION_ID, "SUBMITTED", "VERIFIED", LocalDateTime.now()));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start",
                        LOAN_APPLICATION_ID
                ).with(user("loan-officer").authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFICATION_PENDING"));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete",
                        LOAN_APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "Evidence is consistent."
                                }
                                """)
                        .with(user("loan-officer").authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/start",
                        LOAN_APPLICATION_ID
                ).with(user("customer").authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification/complete",
                        LOAN_APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "VERIFIED",
                                  "assessmentNote": "Evidence is consistent."
                                }
                                """)
                        .with(user("approver").authorities(new SimpleGrantedAuthority("approval:decide"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void allowsOnlyLoanReviewAuthorityForCollateralManualVerificationCommands() throws Exception {
        UUID verificationId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(manageCollateralLoanVerificationUseCase.startManualVerification(LOAN_APPLICATION_ID))
                .thenReturn(new CollateralLoanVerificationStartDto(
                        verificationId,
                        LOAN_APPLICATION_ID,
                        "VERIFICATION_PENDING",
                        "PENDING_MANUAL_REVIEW",
                        new CollateralAssessmentSnapshotDto(
                                "CAR",
                                "Customer vehicle",
                                new BigDecimal("25000000"),
                                "Customer-owned",
                                "Operational condition"
                        )
                ));
        when(manageCollateralLoanVerificationUseCase.completeManualVerification(any(), any()))
                .thenReturn(new CollateralLoanVerificationDto(
                        verificationId,
                        LOAN_APPLICATION_ID,
                        "SUBMITTED",
                        "VERIFIED",
                        LocalDateTime.now()
                ));

        String path = "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/start";
        mockMvc.perform(post(path, LOAN_APPLICATION_ID)
                        .with(user("loan-officer")
                                .authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(verificationId.toString()));

        String completionBody = """
                {
                  "expectedVerificationId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "outcome": "VERIFIED",
                  "assessmentNote": "Ownership evidence is consistent."
                }
                """;
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        LOAN_APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody)
                        .with(user("loan-officer")
                                .authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post(path, LOAN_APPLICATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post(path, LOAN_APPLICATION_ID)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:submit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post(path, LOAN_APPLICATION_ID)
                        .with(user("staff")
                                .authorities(new SimpleGrantedAuthority("loan:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification/complete",
                        LOAN_APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody)
                        .with(user("approver")
                                .authorities(new SimpleGrantedAuthority("approval:decide"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
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

    @Test
    void allowsCustomerWithOfferPermissionsToViewAndAcceptApprovedOffer() throws Exception {
        when(queryApprovedOfferUseCase.getApprovedOffer(LOAN_APPLICATION_ID))
                .thenReturn(approvedOffer("PENDING", List.of("ACCEPT", "DECLINE")));
        when(respondToApprovedOfferUseCase.acceptOffer(LOAN_APPLICATION_ID))
                .thenReturn(new ApprovedOfferActionResult(
                        ApprovedOfferActionOutcome.SUCCESS,
                        approvedOffer("ACCEPTED", List.of())
                ));

        mockMvc.perform(get("/api/v1/loan-applications/{loanApplicationId}/approved-offer", LOAN_APPLICATION_ID)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:read:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approved-offer/accept", LOAN_APPLICATION_ID)
                        .with(user("customer")
                                .authorities(new SimpleGrantedAuthority("loan:offer:respond:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
    @Test
    void allowsApproverWithApprovalDecidePermissionToDecide() throws Exception {
        UUID decisionId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID recommendationId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID approverUserId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        when(submitApprovalDecisionUseCase.submitApprovalDecision(any(), any()))
                .thenReturn(new ApprovalDecisionDto(
                        decisionId,
                        LOAN_APPLICATION_ID,
                        recommendationId,
                        approverUserId,
                        "APPROVE",
                        null,
                        null,
                        LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/v1/loan-applications/{loanApplicationId}/approval-decisions", LOAN_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE"
                                }
                                """)
                        .with(user("approver")
                                .authorities(new SimpleGrantedAuthority("approval:decide"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionId").value(decisionId.toString()))
                .andExpect(jsonPath("$.action").value("APPROVE"));
    }


    private static CustomerBankAccountDto bankAccountDto() {
        return new CustomerBankAccountDto(
                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                "VCB",
                "Vietcombank",
                "Customer Demo",
                "****7890",
                "7890",
                "ACTIVE",
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }
    private static CustomerDto customerDto() {
        return new CustomerDto(
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "CUS-000000001",
                "ACTIVE",
                "UNVERIFIED",
                "COMPLETE",
                false,
                new CustomerProfileDto(
                        "Customer Demo",
                        "0901234567",
                        "1 Meridian Street",
                        "SALARIED",
                        "Meridian Partner Co",
                        true,
                        true
                )
        );
    }
    private static ApprovedOfferDto approvedOffer(String status, List<String> availableActions) {
        return new ApprovedOfferDto(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                LOAN_APPLICATION_ID,
                status,
                java.math.BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                "FLAT_ORIGINAL_PRINCIPAL",
                new java.math.BigDecimal("0.012000"),
                java.math.BigDecimal.valueOf(36_000).setScale(2),
                java.math.BigDecimal.ZERO.setScale(2),
                java.math.BigDecimal.valueOf(3_036_000).setScale(2),
                "ON_SALARY_DATE",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                LocalDateTime.of(2026, 7, 13, 9, 0),
                null,
                null,
                null,
                availableActions,
                List.of()
        );
    }

    private static AuthenticationResult authenticationResult(String accessToken, String refreshToken) {
        Instant issuedAt = Instant.parse("2026-08-24T00:00:00Z");
        return new AuthenticationResult(
                new AuthResponse(
                        "Bearer",
                        accessToken,
                        issuedAt.plusSeconds(3600),
                        UUID.fromString("00000000-0000-0000-0000-000000000301"),
                        "customer.demo@meridian.local",
                        "CUSTOMER",
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        Set.of("CUSTOMER"),
                        Set.of("loan:submit")
                ),
                refreshToken,
                issuedAt,
                issuedAt.plusSeconds(604800)
        );
    }
}

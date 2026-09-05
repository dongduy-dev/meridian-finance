package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationReviewDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationVerificationUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffLoanApplicationReviewController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffLoanApplicationVerificationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        StaffLoanApplicationVerificationController.class,
        StaffLoanApplicationReviewController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class StaffLoanReviewReadSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String VERIFICATION_PATH =
            "/api/v1/staff/loan-applications/{loanApplicationId}/verification";
    private static final String REVIEW_PATH =
            "/api/v1/staff/loan-applications/{loanApplicationId}/review";

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out
            .AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryStaffLoanApplicationVerificationUseCase verificationUseCase;
    @MockitoBean QueryStaffLoanApplicationReviewUseCase reviewUseCase;

    @BeforeEach
    void setUp() {
        when(verificationUseCase.query(any())).thenReturn(verification());
        when(reviewUseCase.query(any())).thenReturn(review());
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get(VERIFICATION_PATH, APPLICATION_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(REVIEW_PATH, APPLICATION_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void exactLoanReviewAuthorityAllowsBothPurposeLimitedReads() throws Exception {
        mockMvc.perform(get(VERIFICATION_PATH, APPLICATION_ID).with(authority("loan:review")))
                .andExpect(status().isOk());
        mockMvc.perform(get(REVIEW_PATH, APPLICATION_ID).with(authority("loan:review")))
                .andExpect(status().isOk());
    }

    @Test
    void loanReadRecommendationAndPrefixAuthoritiesRemainForbidden() throws Exception {
        for (String denied : List.of(
                "loan:read",
                "approval:recommend",
                "document:review",
                "loan:review:all"
        )) {
            mockMvc.perform(get(VERIFICATION_PATH, APPLICATION_ID).with(authority(denied)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(REVIEW_PATH, APPLICATION_ID).with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    private static StaffLoanApplicationVerificationDto verification() {
        return new StaffLoanApplicationVerificationDto(
                APPLICATION_ID,
                "SA-20260905-000001",
                "SALARY_ADVANCE",
                "SALARY_BASED",
                BigDecimal.ONE,
                1,
                "SUBMITTED",
                LocalDateTime.of(2026, 9, 5, 8, 0),
                new StaffLoanApplicationVerificationDto.DocumentReadinessDto(true, true),
                new StaffLoanApplicationVerificationDto.ActionPresentationDto(false, false),
                new StaffLoanApplicationVerificationDto.SalaryAdvanceVerificationDto(
                        1,
                        "MATCHED_ACTIVE",
                        "VERIFIED",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        new BigDecimal("9"),
                        LocalDateTime.of(2026, 9, 5, 8, 0)
                ),
                List.of()
        );
    }

    private static StaffLoanApplicationReviewDto review() {
        return new StaffLoanApplicationReviewDto(
                APPLICATION_ID,
                "SA-20260905-000001",
                "SALARY_ADVANCE",
                "SALARY_BASED",
                BigDecimal.ONE,
                1,
                "SUBMITTED",
                LocalDateTime.of(2026, 9, 5, 8, 0),
                new StaffLoanApplicationReviewDto.DocumentReadinessDto(true, true),
                new StaffLoanApplicationReviewDto.ProductReadinessDto("VERIFIED", true),
                true,
                null
        );
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

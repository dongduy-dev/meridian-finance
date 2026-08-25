package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.QuerySalaryAdvanceReadinessUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanApplicationController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.SalaryAdvanceReadinessController;
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
        SalaryAdvanceReadinessController.class,
        LoanApplicationController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class LoanWorkflowReadSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QuerySalaryAdvanceReadinessUseCase readiness;
    @MockitoBean QueryLoanApplicationUseCase applications;

    @BeforeEach
    void setUp() {
        when(readiness.queryReadiness()).thenReturn(readinessResult());
        when(applications.query(any())).thenReturn(applicationResult());
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products/salary-advance/readiness"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/loan-applications/{id}", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readinessRequiresOnlyLoanSubmitPermission() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products/salary-advance/readiness")
                        .with(authority("loan:submit")))
                .andExpect(status().isOk());

        for (String denied : List.of("loan:read:own", "loan:read", "approval:decide")) {
            mockMvc.perform(get("/api/v1/loan-products/salary-advance/readiness")
                            .with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void applicationReadAcceptsOwnOrStaffReadAndRejectsUnrelatedPermissions() throws Exception {
        for (String allowed : List.of("loan:read:own", "loan:read")) {
            mockMvc.perform(get("/api/v1/loan-applications/{id}", APPLICATION_ID)
                            .with(authority(allowed)))
                    .andExpect(status().isOk());
        }

        for (String denied : List.of(
                "loan:submit", "repayment:update", "approval:decide", "document:read"
        )) {
            mockMvc.perform(get("/api/v1/loan-applications/{id}", APPLICATION_ID)
                            .with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static SalaryAdvanceReadinessDto readinessResult() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new SalaryAdvanceReadinessDto(
                "SALARY_ADVANCE", null, "NOT_VERIFIED", "NOT_VERIFIED", "UNAVAILABLE",
                zero, zero, zero, zero, null, false,
                List.of("EMPLOYEE_NOT_VERIFIED", "SALARY_ADVANCE_LIMIT_UNAVAILABLE")
        );
    }

    private static LoanApplicationStatusDto applicationResult() {
        return new LoanApplicationStatusDto(
                APPLICATION_ID,
                "SA-20260810-000001",
                "SALARY_ADVANCE",
                "SALARY_BASED",
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                "UNDER_REVIEW",
                LocalDateTime.of(2026, 8, 10, 8, 0)
        );
    }
}

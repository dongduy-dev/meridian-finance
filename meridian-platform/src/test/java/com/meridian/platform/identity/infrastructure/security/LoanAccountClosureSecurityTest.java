package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanAccountClosureApiMapper;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanAccountClosureController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanAccountClosureController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanAccountClosureApiMapper.class
})
class LoanAccountClosureSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean CloseLoanAccountUseCase closures;

    @BeforeEach
    void setUp() {
        when(closures.close(any())).thenReturn(new CloseLoanAccountUseCase.Result(
                APPLICATION_ID,
                UUID.randomUUID(),
                LoanAccountStatus.CLOSED,
                LocalDateTime.of(2026, 9, 2, 10, 0),
                false
        ));
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(request().content(body()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyLoanAccountClosePermissionCanExecute() throws Exception {
        mockMvc.perform(request()
                        .with(authority("loan:account:close"))
                        .content(body()))
                .andExpect(status().isOk());
        verify(closures).close(any());

        for (String denied : List.of(
                "repayment:update",
                "loan:settlement:approve",
                "loan:read",
                "approval:decide"
        )) {
            mockMvc.perform(request()
                            .with(authority(denied))
                            .content(body()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void malformedOrMissingInputUsesSafeValidationEnvelope() throws Exception {
        String path = "/api/v1/loan-applications/" + APPLICATION_ID
                + "/loan-account/closure";
        mockMvc.perform(request()
                        .with(authority("loan:account:close"))
                        .content("{\"requestId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value(path));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/loan-account/closure",
                        "not-a-uuid"
                )
                        .with(authority("loan:account:close"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder request() {
        return post(
                "/api/v1/loan-applications/{id}/loan-account/closure",
                APPLICATION_ID
        ).contentType(MediaType.APPLICATION_JSON);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String authority
    ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static String body() {
        return """
                {"requestId":"40000000-0000-0000-0000-000000000001"}
                """;
    }
}

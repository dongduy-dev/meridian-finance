package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanApplicationCancellationApiMapper;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanApplicationCancellationController;
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

@WebMvcTest(controllers = LoanApplicationCancellationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanApplicationCancellationApiMapper.class
})
class LoanApplicationCancellationSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean CancelLoanApplicationUseCase cancellations;

    @BeforeEach
    void setUp() {
        when(cancellations.cancel(any())).thenReturn(new CancelLoanApplicationUseCase.Result(
                APPLICATION_ID,
                LoanApplicationStatus.CANCELLED,
                LocalDateTime.of(2026, 8, 10, 9, 0),
                false
        ));
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(request().content(body()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOwnCancellationPermissionCanReachUseCase() throws Exception {
        mockMvc.perform(request()
                        .with(authority("loan:cancel:own"))
                        .content(body()))
                .andExpect(status().isOk());
        verify(cancellations).cancel(any());

        for (String denied : List.of(
                "loan:read",
                "approval:decide",
                "repayment:update",
                "loan:account:close"
        )) {
            mockMvc.perform(request()
                            .with(authority(denied))
                            .content(body()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void malformedOrMissingInputUsesSafeValidationEnvelope() throws Exception {
        String path = "/api/v1/loan-applications/" + APPLICATION_ID + "/cancel";
        mockMvc.perform(request()
                        .with(authority("loan:cancel:own"))
                        .content("{\"requestId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value(path));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/cancel",
                        "not-a-uuid"
                )
                        .with(authority("loan:cancel:own"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder request() {
        return post(
                "/api/v1/loan-applications/{id}/cancel",
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

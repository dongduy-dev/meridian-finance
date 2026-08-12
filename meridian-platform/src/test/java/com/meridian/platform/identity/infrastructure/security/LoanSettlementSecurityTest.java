package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanSettlementApiMapper;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanSettlementController;
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
import java.time.LocalDate;
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

@WebMvcTest(controllers = LoanSettlementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanSettlementApiMapper.class
})
class LoanSettlementSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean ApproveLoanSettlementUseCase settlements;

    @BeforeEach
    void setUp() {
        when(settlements.approve(any())).thenReturn(result());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        APPLICATION_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyLoanSettlementApprovePermissionCanExecute() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        APPLICATION_ID
                )
                        .with(authority("loan:settlement:approve"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk());
        verify(settlements).approve(any());

        for (String denied : List.of(
                "approval:decide",
                "repayment:update",
                "loan:read",
                "loan:read:own",
                "loan:disburse"
        )) {
            mockMvc.perform(post(
                            "/api/v1/loan-applications/{id}/settlements",
                            APPLICATION_ID
                    )
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void malformedOrMissingTransportInputUsesSafeValidationEnvelope()
            throws Exception {
        String path = "/api/v1/loan-applications/" + APPLICATION_ID + "/settlements";
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        APPLICATION_ID
                )
                        .with(authority("loan:settlement:approve"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value(path));

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/settlements",
                        "not-a-uuid"
                )
                        .with(authority("loan:settlement:approve"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String authority
    ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static String body() {
        return """
                {
                  "requestId": "40000000-0000-0000-0000-000000000001",
                  "expectedSettlementAmount": 1230000,
                  "paymentValueDate": "2026-09-01",
                  "externalPaymentReference": "SETTLEMENT-REF"
                }
                """;
    }

    private static ApproveLoanSettlementUseCase.Result result() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        BigDecimal zero = money("0");
        return new ApproveLoanSettlementUseCase.Result(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                money("1230000"), date, approvedAt, money("1200000"), money("1200000"),
                new ApproveLoanSettlementUseCase.AccountBalance(
                        money("1200000"), money("30000"), zero, money("1230000"),
                        zero, zero, zero, zero, date, approvedAt, date,
                        LoanAccountStatus.SETTLED
                ),
                false
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

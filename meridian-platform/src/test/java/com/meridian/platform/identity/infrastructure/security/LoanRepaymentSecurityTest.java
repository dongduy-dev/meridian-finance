package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanRepaymentApiMapper;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanRepaymentController;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanRepaymentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanRepaymentApiMapper.class
})
class LoanRepaymentSecurityTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean RecordRepaymentUseCase record;
    @MockitoBean QueryRepaymentsUseCase query;

    @BeforeEach
    void setUp() {
        when(record.record(any())).thenReturn(recordResult());
        when(query.query(any(), anyInt(), anyInt()))
                .thenReturn(new QueryRepaymentsUseCase.PageResult(0, 20, 0, 0, List.of()));
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRepaymentUpdateCanPost() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());

        for (String denied : List.of("loan:read:own", "loan:read", "loan:disburse")) {
            mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void ownerAndStaffReadAuthoritiesCanReadHistory() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("loan:read:own")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("loan:read")))
                .andExpect(status().isOk());

        for (String denied : List.of("repayment:update", "loan:disburse")) {
            mockMvc.perform(get("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                            .with(authority(denied)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void malformedTransportInputUsesStableSafeEnvelope() throws Exception {
        String safePath = "/api/v1/loan-applications/{loanApplicationId}/repayments";
        String invalidId = "not-a-uuid";
        assertValidationEnvelope(mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", invalidId)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON).content(body())), safePath, invalidId);
        assertValidationEnvelope(mockMvc.perform(get(
                        "/api/v1/loan-applications/{id}/repayments", invalidId)
                        .with(authority("loan:read"))), safePath, invalidId);

        assertValidationEnvelope(mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON).content("not-json")),
                safePath, "not-json");
        assertValidationEnvelope(mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("2026-08-01", "not-a-date"))),
                safePath, "not-a-date");
        assertValidationEnvelope(mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("100,", "{\"secret\":true},"))),
                safePath, "secret");
        assertValidationEnvelope(mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"40000000-0000-0000-0000-000000000001\"")),
                safePath, "40000000-0000-0000-0000-000000000001");
    }

    @Test
    void establishedBeanAndPaginationValidationEnvelopesRemainStable() throws Exception {
        String path = "/api/v1/loan-applications/" + APPLICATION_ID + "/repayments";
        assertValidationEnvelope(mockMvc.perform(get(path + "?size=101")
                .with(authority("loan:read"))), path, null);
        assertValidationEnvelope(mockMvc.perform(post(path)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":null}")), path, null);
    }

    @Test
    void validTransportStillReachesApplicationPath() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/repayments", APPLICATION_ID)
                        .with(authority("repayment:update"))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
        verify(record).record(any());
    }

    private static void assertValidationEnvelope(
            org.springframework.test.web.servlet.ResultActions result,
            String expectedPath,
            String prohibitedEvidence
    ) throws Exception {
        String response = result
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Input validation failed."))
                .andExpect(jsonPath("$.path").value(expectedPath))
                .andReturn().getResponse().getContentAsString();
        if (prohibitedEvidence != null) {
            assertFalse(response.contains(prohibitedEvidence));
        }
        assertFalse(response.contains("HttpMessageNotReadableException"));
        assertFalse(response.contains("MethodArgumentTypeMismatchException"));
        assertFalse(response.contains("Jackson"));
        assertFalse(response.contains("SQLException"));
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
                  "externalPaymentReference": "PAY-REF-01",
                  "amount": 100,
                  "paymentValueDate": "2026-08-01"
                }
                """;
    }

    private static RecordRepaymentUseCase.Result recordResult() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        LocalDateTime recordedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        return new RecordRepaymentUseCase.Result(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                money(100), date, recordedAt, List.of(), List.of(),
                new RecordRepaymentUseCase.AccountBalance(
                        money(100), money(0), money(0), money(100), money(900), money(0),
                        money(0), money(900), date, recordedAt, date,
                        LoanAccountStatus.ACTIVE), money(100), money(100), false);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}

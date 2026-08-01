package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.mapper.LoanDisbursementApiMapper;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.infrastructure.adapter.in.web.LoanDisbursementController;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanDisbursementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class,
        LoanDisbursementApiMapper.class
})
class LoanDisbursementSecurityTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean ConfirmManualDisbursementUseCase confirm;
    @MockitoBean RevealDisbursementDestinationUseCase reveal;
    @MockitoBean QueryLoanAccountUseCase query;

    @BeforeEach
    void setUp() {
        when(confirm.confirm(any())).thenReturn(confirmationResult());
        when(reveal.reveal(any())).thenReturn(revealResult());
        when(query.query(any())).thenReturn(queryResult());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts/current/"
                        + "disbursement-destination/reveal", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(revealBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsOnlyLoanDisburseForConfirmationAndReveal() throws Exception {
        var accounting = authority("loan:disburse");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .with(accounting)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts/current/"
                        + "disbursement-destination/reveal", APPLICATION_ID)
                        .with(authority("loan:disburse"))
                        .contentType(MediaType.APPLICATION_JSON).content(revealBody()))
                .andExpect(status().isOk());

        for (String denied : List.of("loan:read:own", "loan:review", "approval:decide")) {
            mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts/current/"
                            + "disbursement-destination/reveal", APPLICATION_ID)
                            .with(authority(denied))
                            .contentType(MediaType.APPLICATION_JSON).content(revealBody()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allowsOwnerAndStaffLoanAccountReadPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID)
                        .with(authority("loan:read:own")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID)
                        .with(authority("loan:read")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", APPLICATION_ID)
                        .with(authority("loan:review")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedIdentifiersAndBodies() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/not-a-uuid/loan-account")
                        .with(authority("loan:read")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", APPLICATION_ID)
                        .with(authority("loan:disburse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":null,\"expectedContractVersion\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts/current/"
                        + "disbursement-destination/reveal", APPLICATION_ID)
                        .with(authority("loan:disburse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedContractVersion\":0}"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String authority
    ) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static String confirmBody() {
        return """
                {
                  "requestId": "%s",
                  "expectedContractVersion": 1,
                  "externalTransferReference": "BANK-REFERENCE",
                  "disbursementValueDate": "2026-07-28",
                  "firstRepaymentDate": "2026-08-28"
                }
                """.formatted(UUID.randomUUID());
    }

    private static String revealBody() {
        return "{\"expectedContractVersion\":1}";
    }

    private static ConfirmManualDisbursementUseCase.Result confirmationResult() {
        return new ConfirmManualDisbursementUseCase.Result(
                APPLICATION_ID, LoanApplicationStatus.DISBURSED, UUID.randomUUID(),
                "LA-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", LoanAccountStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 28, 10, 0), UUID.randomUUID(), money(1_000),
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 28), UUID.randomUUID(),
                RepaymentScheduleType.FINAL, 1,
                List.of(new ConfirmManualDisbursementUseCase.ScheduleItem(
                        UUID.randomUUID(), UUID.randomUUID(), 1, LocalDate.of(2026, 8, 28),
                        money(1_000), money(100), money(0), money(1_100)
                )), false
        );
    }

    private static RevealDisbursementDestinationUseCase.Result revealResult() {
        return new RevealDisbursementDestinationUseCase.Result(
                APPLICATION_ID, UUID.randomUUID(), 1, "VCB", "Meridian Test Bank",
                "MERIDIAN CUSTOMER", "01234567890"
        );
    }

    private static QueryLoanAccountUseCase.Result queryResult() {
        return new QueryLoanAccountUseCase.Result(
                APPLICATION_ID, UUID.randomUUID(), "LA-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                LoanAccountStatus.ACTIVE, LocalDateTime.of(2026, 7, 28, 10, 0),
                money(1_000), 1, money(100), money(0), money(1_100),
                new QueryLoanAccountUseCase.ServicingSummary(
                        money(0), money(0), money(0), money(0), money(1_000),
                        money(100), money(0), money(1_100),
                        LocalDate.of(2026, 7, 28), null, null),
                new QueryLoanAccountUseCase.DestinationSummary(
                        "VCB", "Meridian Test Bank", "MERIDIAN CUSTOMER", "********"),
                UUID.randomUUID(), RepaymentScheduleType.FINAL, 1,
                LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 28),
                List.of(new QueryLoanAccountUseCase.ScheduleItem(
                        1, LocalDate.of(2026, 8, 28), money(1_000), money(100),
                        money(0), money(1_100),
                        new QueryLoanAccountUseCase.InstallmentServicing(
                                money(0), money(0), money(0), money(0),
                                money(1_000), money(100), money(0), money(1_100),
                                RepaymentInstallmentStatus.NOT_DUE,
                                LocalDate.of(2026, 7, 28), null, null)))
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}

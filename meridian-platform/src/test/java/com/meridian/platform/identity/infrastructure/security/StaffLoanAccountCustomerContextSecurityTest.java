package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.ServicingCustomerContextPort;
import com.meridian.platform.loan.application.service.QueryStaffLoanAccountCustomerContextService;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffLoanAccountCustomerContextController;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StaffLoanAccountCustomerContextController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class,
        QueryStaffLoanAccountCustomerContextService.class})
class StaffLoanAccountCustomerContextSecurityTest {
    private static final UUID APP = UUID.fromString("11111111-1111-4111-8111-111111111111"),
            ACCOUNT = UUID.fromString("22222222-2222-4222-8222-222222222222"),
            CUSTOMER = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final String PATH = "/api/v1/staff/loan-applications/" + APP + "/servicing-context";
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean LoanApplicationRepository applications;
    @MockitoBean LoanAccountRepository accounts;
    @MockitoBean ServicingCustomerContextPort customers;
    @MockitoBean CurrentUserProvider users;

    @BeforeEach void setUp() {
        var application = mock(LoanApplication.class);
        var account = mock(LoanAccount.class);
        when(applications.findById(APP)).thenReturn(Optional.of(application));
        when(application.id()).thenReturn(APP);
        when(application.customerId()).thenReturn(CUSTOMER);
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSED);
        when(accounts.findByLoanApplicationId(APP)).thenReturn(Optional.of(account));
        when(account.id()).thenReturn(ACCOUNT);
        when(account.loanApplicationId()).thenReturn(APP);
        when(account.customerId()).thenReturn(CUSTOMER);
        when(customers.findBusinessIdentityByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.BusinessIdentity("CUS-001", "Ari Customer")));
        when(customers.findCurrentContactByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.CurrentContact("CUS-001", "Ari Customer", "0901234567")));
    }

    @Test void anonymousAndMissingLoanReadAreDenied() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (String permission : new String[]{"loan:read:own", "repayment:update", "customer:read", "loan:settlement:approve"}) {
            mvc.perform(get(PATH).with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(applications, accounts, customers);
    }

    @Test void accountingRepaymentAndClosureReceiveOnlyCurrentContact() throws Exception {
        for (String permission : new String[]{"repayment:update", "loan:account:close"}) {
            authorized(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", permission))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer.customerNumber").value("CUS-001"))
                    .andExpect(jsonPath("$.customer.fullName").value("Ari Customer"))
                    .andExpect(jsonPath("$.customer.phoneNumber").value("0901234567"))
                    .andExpect(jsonPath("$.customer.length()").value(3));
        }
        assertSafe(authorized(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "repayment:update")));
    }

    @Test void approverSettlementHasIdentityButNoActualPhone() throws Exception {
        var result = authorized(Set.of("APPROVER"), Set.of("loan:read", "loan:settlement:approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.customerNumber").value("CUS-001"))
                .andExpect(jsonPath("$.customer.fullName").value("Ari Customer"))
                .andExpect(jsonPath("$.customer.phoneNumber").doesNotExist());
        assertFalse(result.andReturn().getResponse().getContentAsString().contains("0901234567"));
        verify(customers, never()).findCurrentContactByCustomerId(any());
        assertSafe(result);
    }

    @Test void explicitCustomerReadReceivesOnlyTheNarrowCurrentContact() throws Exception {
        var result = authorized(Set.of("LOAN_OFFICER"), Set.of("loan:read", "customer:read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer.phoneNumber").value("0901234567"));
        assertSafe(result);
    }

    @Test void genericReadAndUnmatchedRolesOrCapabilitiesReturnNoCustomer() throws Exception {
        authorized(Set.of(), Set.of("loan:read")).andExpect(status().isOk()).andExpect(jsonPath("$.customer").doesNotExist());
        authorized(Set.of("APPROVER", "ACCOUNTING_OFFICER"), Set.of("loan:read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer").doesNotExist());
        authorized(Set.of(), Set.of("loan:read", "repayment:update", "loan:account:close", "loan:settlement:approve"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer").doesNotExist());
        verifyNoInteractions(customers);
    }

    @Test void customerCannotUseStaffContextEvenWithMisgrantedLoanRead() throws Exception {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "customer@test", "CUSTOMER",
                CUSTOMER, Set.of("CUSTOMER"), Set.of("loan:read", "customer:read")));
        mvc.perform(get(PATH).with(user("customer").authorities(new SimpleGrantedAuthority("loan:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(applications, accounts, customers);
    }

    @Test void entitledMissingCustomerFailsClosedWithSafeConflict() throws Exception {
        when(customers.findCurrentContactByCustomerId(CUSTOMER)).thenReturn(Optional.empty());
        authorized(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "repayment:update"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SYSTEM_STATE_CONFLICT"));
    }

    private ResultActions authorized(Set<String> roles, Set<String> permissions) throws Exception {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "staff@test", "STAFF", null, roles, permissions));
        return mvc.perform(get(PATH).with(user("staff").authorities(permissions.stream()
                .map(SimpleGrantedAuthority::new).toList())));
    }
    private static void assertSafe(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.loanApplicationId").value(APP.toString()))
                .andExpect(jsonPath("$.loanAccountId").value(ACCOUNT.toString()));
        String json = result.andReturn().getResponse().getContentAsString();
        for (String forbidden : new String[]{CUSTOMER.toString(), "customerId", "userId", "identityReference", "email", "address", "bank", "fingerprint", "ciphertext"}) {
            assertFalse(json.contains(forbidden), forbidden);
        }
    }
}

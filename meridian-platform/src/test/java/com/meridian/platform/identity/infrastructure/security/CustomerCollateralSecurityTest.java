package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.service.QueryOwnCollateralService;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.loan.infrastructure.adapter.in.web.CustomerCollateralController;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CustomerCollateralController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class, QueryOwnCollateralService.class})
class CustomerCollateralSecurityTest {
    private static final UUID APP = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String PATH = "/api/v1/loan-applications/" + APP + "/collateral";
    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 10, 5, 8, 0);
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean LoanApplicationRepository applications;
    @MockitoBean CollateralRepository collaterals;
    @MockitoBean CurrentUserProvider users;

    @Test
    void anonymousAndMissingExactPermissionAreDeniedBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (String permission : List.of("loan:read", "loan:submit", "document:read", "approval:decide")) {
            mvc.perform(get(PATH).with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, applications, collaterals);
    }

    @ParameterizedTest
    @EnumSource(OriginationChannel.class)
    void customerReadsSafeFactsForBothChannelsAndRequestCustomerIdCannotOverrideOwnership(OriginationChannel channel) throws Exception {
        customer(CUSTOMER);
        application(channel);
        when(collaterals.findByLoanApplicationId(APP)).thenReturn(List.of(fact(APP)));
        mvc.perform(get(PATH).param("customerId", UUID.randomUUID().toString()).with(ownAuthority()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.collateralType").value("CAR"))
                .andExpect(jsonPath("$.description").value("Submitted car"))
                .andExpect(jsonPath("$.estimatedValue").value(350_000_000))
                .andExpect(jsonPath("$.ownershipStatus").value("Customer owned"))
                .andExpect(jsonPath("$.conditionNote").value("Normal used condition"));
        verify(applications, never()).save(any());
        verify(applications, never()).acquireWorkflowLock(any());
        verify(collaterals, never()).save(any());
    }

    @Test
    void staffEvenWithOwnReadAuthorityCannotUseCustomerEndpoint() throws Exception {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "staff@example.test",
                "STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:read:own", "loan:read")));
        mvc.perform(get(PATH).with(ownAuthority())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_ACCESS_DENIED"));
        verifyNoInteractions(applications, collaterals);
    }

    @Test
    void foreignAndMissingApplicationReturnSameConcealedNotFound() throws Exception {
        application(OriginationChannel.CUSTOMER_DIGITAL);
        customer(UUID.randomUUID());
        mvc.perform(get(PATH).param("customerId", CUSTOMER.toString()).with(ownAuthority()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));
        customer(CUSTOMER);
        when(applications.findById(APP)).thenReturn(Optional.empty());
        mvc.perform(get(PATH).with(ownAuthority())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));
        verifyNoInteractions(collaterals);
    }

    @Test
    void inconsistentCollateralRowsReturn409SystemStateConflict() throws Exception {
        customer(CUSTOMER);
        application(OriginationChannel.CUSTOMER_DIGITAL);
        for (List<Collateral> facts : List.of(List.<Collateral>of(), List.of(fact(APP), fact(APP)), List.of(fact(UUID.randomUUID())))) {
            when(collaterals.findByLoanApplicationId(APP)).thenReturn(facts);
            mvc.perform(get(PATH).with(ownAuthority())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("SYSTEM_STATE_CONFLICT"));
        }
    }

    private void customer(UUID id) {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "customer@example.test",
                "CUSTOMER", id, Set.of("CUSTOMER"), Set.of("loan:read:own")));
    }

    private void application(OriginationChannel channel) {
        when(applications.findById(APP)).thenReturn(Optional.of(new LoanApplication(APP, CUSTOMER,
                UUID.randomUUID(), "CL-20261005-000001", ProductCode.COLLATERAL_LOAN, ProductType.SECURED,
                channel, LoanApplicationStatus.SUBMITTED, BigDecimal.valueOf(25_000_000), 12, SUBMITTED)));
    }

    private static Collateral fact(UUID id) {
        return new Collateral(UUID.randomUUID(), id, CollateralType.CAR, "Submitted car",
                BigDecimal.valueOf(350_000_000), "Customer owned", "Normal used condition", SUBMITTED);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ownAuthority() {
        return user("customer").authorities(new SimpleGrantedAuthority("loan:read:own"));
    }
}

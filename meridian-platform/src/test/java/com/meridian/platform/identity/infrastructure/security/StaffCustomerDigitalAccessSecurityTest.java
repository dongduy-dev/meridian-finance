package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;
import com.meridian.platform.identity.application.port.in.ManageCustomerDigitalAccessUseCase;
import com.meridian.platform.identity.infrastructure.adapter.in.web.StaffCustomerDigitalAccessController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffCustomerDigitalAccessController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class})
class StaffCustomerDigitalAccessSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean ManageCustomerDigitalAccessUseCase useCase;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String PATH = "/api/v1/staff/customers/{id}/digital-access";
    private static final String BODY = "{\"email\":\"customer@example.com\",\"identityReference\":\"ID123\"}";

    @Test
    void anonymousAndLookalikeAuthoritiesCannotReadOrEnable() throws Exception {
        mvc.perform(get(PATH, CUSTOMER_ID)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH, CUSTOMER_ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        var customer = user("customer").authorities(new SimpleGrantedAuthority("customer:read:own"));
        mvc.perform(get(PATH, CUSTOMER_ID).with(customer)).andExpect(status().isForbidden());
        mvc.perform(post(PATH, CUSTOMER_ID).with(customer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        for (String authority : new String[] {"LOAN_OFFICER", "customer:intake", "customer:intake:manage:all", "customer:read"}) {
            var actor = user("staff").authorities(new SimpleGrantedAuthority(authority));
            mvc.perform(get(PATH, CUSTOMER_ID).with(actor)).andExpect(status().isForbidden());
            mvc.perform(post(PATH, CUSTOMER_ID).with(actor).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void exactPermissionAllowsNarrowProjectionAndRejectsPasswordFields() throws Exception {
        var actor = user("staff").authorities(new SimpleGrantedAuthority("customer:intake:manage"));
        when(useCase.status(CUSTOMER_ID)).thenReturn(CustomerDigitalAccessDto.absent(CUSTOMER_ID));
        when(useCase.enable(eq(CUSTOMER_ID), any())).thenReturn(
                new CustomerDigitalAccessDto(CUSTOMER_ID, true, "customer@example.com", false));
        mvc.perform(get(PATH, CUSTOMER_ID).with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.identityReference").doesNotExist());
        mvc.perform(post(PATH, CUSTOMER_ID).with(actor).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.identityReference").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post(PATH, CUSTOMER_ID).with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\",\"identityReference\":\"ID123\",\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }
}

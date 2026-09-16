package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.dto.AssignableInternalRoleDto;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.port.in.ManageInternalUserUseCase;
import com.meridian.platform.identity.application.port.in.QueryInternalUsersUseCase;
import com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository;
import com.meridian.platform.identity.infrastructure.adapter.in.web.InternalUserAdministrationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalUserAdministrationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class InternalUserAdministrationSecurityTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryInternalUsersUseCase query;
    @MockitoBean ManageInternalUserUseCase commands;

    @BeforeEach
    void setUp() {
        when(query.findAll()).thenReturn(List.of(userDto()));
        when(query.findAssignableRoles()).thenReturn(List.of(new AssignableInternalRoleDto(
                "LOAN_OFFICER", "Loan Officer"
        )));
        when(commands.changeStatus(eq(USER_ID), any())).thenReturn(userDto());
        when(commands.changeRoleAssignment(eq(USER_ID), eq("LOAN_OFFICER"), any())).thenReturn(userDto());
    }

    @Test
    void exactIdentityUserManageAllowsEveryContract() throws Exception {
        var actor = user("actor").authorities(new SimpleGrantedAuthority("identity:user:manage"));

        mockMvc.perform(get("/api/v1/admin/internal-users").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].authorizationVersion").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/internal-users/assignable-roles").with(actor))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/internal-users/{userId}/status", USER_ID)
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/internal-users/{userId}/roles/{roleCode}", USER_ID, "LOAN_OFFICER")
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"assigned\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousRoleNameAndLookalikeAuthoritiesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/internal-users")).andExpect(status().isUnauthorized());
        for (String denied : List.of(
                "BACK_OFFICE_ADMIN", "identity:user", "identity:user:manage:all", "admin:config", "audit:read"
        )) {
            mockMvc.perform(get("/api/v1/admin/internal-users")
                            .with(user("actor").authorities(new SimpleGrantedAuthority(denied))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void malformedTransportUsesValidationBoundary() throws Exception {
        var actor = user("actor").authorities(new SimpleGrantedAuthority("identity:user:manage"));
        mockMvc.perform(put("/api/v1/admin/internal-users/{userId}/status", "not-a-uuid")
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/internal-users/{userId}/status", USER_ID)
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/internal-users/{userId}/roles/{roleCode}", USER_ID, "LOAN_OFFICER")
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static InternalUserDto userDto() {
        return new InternalUserDto(
                USER_ID,
                "loan.officer@meridian.local",
                "Loan Officer Demo",
                "ACTIVE",
                List.of("LOAN_OFFICER")
        );
    }
}

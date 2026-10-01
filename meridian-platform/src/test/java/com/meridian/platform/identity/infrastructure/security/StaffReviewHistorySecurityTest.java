package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.approval.application.dto.StaffReviewHistoryDto;
import com.meridian.platform.approval.application.port.in.QueryStaffReviewHistoryUseCase;
import com.meridian.platform.approval.infrastructure.adapter.in.web.StaffReviewHistoryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffReviewHistoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class})
class StaffReviewHistorySecurityTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository revocations;
    @MockitoBean QueryStaffReviewHistoryUseCase history;

    @Test
    void eachExactPurposeCanReadWithoutGenericLoanReadAndResponsesAreNonCacheable() throws Exception {
        when(history.query(APPLICATION_ID)).thenReturn(new StaffReviewHistoryDto(APPLICATION_ID, "UCL-HISTORY",
                "UNDER_REVIEW", List.of()));
        for (String permission : List.of("loan:review", "approval:recommend", "approval:decide")) {
            mockMvc.perform(get(path()).with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                    .andExpect(jsonPath("$.cycles").isEmpty());
        }
    }

    @Test
    void unrelatedPermissionsRolesAndPrefixesCannotRead() throws Exception {
        for (String permission : List.of("loan:read", "repayment:update", "partner:read", "APPROVER",
                "loan:review:all", "approval:recommend:all", "approval:decide:all")) {
            mockMvc.perform(get(path()).with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(history);
    }

    @Test
    void anonymousRequestIsDeniedBeforeComposition() throws Exception {
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }

    @Test
    void serializationOmitsDeniedNotesAndDistinguishesPermittedEmptyNotes() throws Exception {
        UUID cycle = UUID.randomUUID();
        UUID recommendation = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.of(2026, 10, 1, 8, 0);
        when(history.query(APPLICATION_ID)).thenReturn(new StaffReviewHistoryDto(APPLICATION_ID, "UCL-HISTORY",
                "RETURNED_TO_REVIEW", List.of(new StaffReviewHistoryDto.CycleDto(cycle, 1, null, "COMPLETED",
                time, time.plusHours(1), new StaffReviewHistoryDto.RecommendationDto(recommendation, cycle,
                "RECOMMEND_APPROVAL", "Normal recommendation reason", null, false, null, null, time),
                new StaffReviewHistoryDto.DecisionDto(UUID.randomUUID(), recommendation,
                "RETURN_TO_LOAN_OFFICER_REVIEW", "Normal decision reason", null, true, null, null, time)))));
        mockMvc.perform(get(path()).with(user("actor").authorities(new SimpleGrantedAuthority("loan:review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycles[0].recommendation.reason").value("Normal recommendation reason"))
                .andExpect(jsonPath("$.cycles[0].decision.reason").value("Normal decision reason"))
                .andExpect(jsonPath("$.cycles[0].recommendation.internalNoteReadable").value(false))
                .andExpect(jsonPath("$.cycles[0].decision.internalNoteReadable").value(true))
                .andExpect(jsonPath("$.cycles[0].recommendation.internalNotes").doesNotExist())
                .andExpect(jsonPath("$.cycles[0].decision.internalNotes").doesNotExist());
    }

    private static String path() {
        return "/api/v1/staff/loan-applications/" + APPLICATION_ID + "/review-history";
    }
}

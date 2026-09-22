package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDto;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewPageDto;
import com.meridian.platform.partner.application.port.in.DecidePartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.infrastructure.adapter.in.web.PartnerEligibilityReviewController;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PartnerEligibilityReviewController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class PartnerEligibilityReviewSecurityTest {

    private static final UUID REVIEW_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryPartnerEligibilityReviewUseCase queryReviews;
    @MockitoBean DecidePartnerEligibilityReviewUseCase decideReview;

    @BeforeEach
    void setUp() {
        when(queryReviews.queryReviews(anyString(), anyInt(), anyInt()))
                .thenReturn(new PartnerEligibilityReviewPageDto(0, 20, 0, 0, List.of()));
        when(queryReviews.queryReview(REVIEW_ID)).thenReturn(null);
        when(decideReview.decide(any(), any())).thenReturn(null);
    }

    @Test
    void readAuthorityCanListAndInspectButCannotDecide() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partner-eligibility-reviews")
                        .with(authority("partner:read")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/partner-eligibility-reviews/{reviewId}", REVIEW_ID)
                        .with(authority("partner:read")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/partner-eligibility-reviews/{reviewId}/decision", REVIEW_ID)
                        .with(authority("partner:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejection()))
                .andExpect(status().isForbidden());
    }

    @Test
    void manageAuthorityCanDecideButDoesNotImplyRead() throws Exception {
        mockMvc.perform(post("/api/v1/admin/partner-eligibility-reviews/{reviewId}/decision", REVIEW_ID)
                        .with(authority("partner:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejection()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/partner-eligibility-reviews")
                        .with(authority("partner:manage")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookalikeAuthoritiesCannotReadOrDecide() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partner-eligibility-reviews")
                        .with(authority("partner:read:all")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/partner-eligibility-reviews/{reviewId}/decision", REVIEW_ID)
                        .with(authority("BACK_OFFICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejection()))
                .andExpect(status().isForbidden());
    }

    private static String rejection() {
        return """
                {"outcome":"REJECT","partnerEmployeeId":null,"reasonCode":"NO_ELIGIBLE_CURRENT_EMPLOYEE"}
                """;
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String authority) {
        return user("actor").authorities(new SimpleGrantedAuthority(authority));
    }
}

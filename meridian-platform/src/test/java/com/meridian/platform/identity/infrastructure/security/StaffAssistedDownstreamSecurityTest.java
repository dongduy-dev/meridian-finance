package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.document.application.port.in.ManageAssistedActionEvidenceUseCase;
import com.meridian.platform.document.infrastructure.adapter.in.web.StaffAssistedActionEvidenceController;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.QueryAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.loan.application.port.in.RecordAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.loan.application.port.in.RecordAssistedLoanContractAcknowledgmentUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffAssistedContractAcknowledgmentController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffAssistedOfferResponseController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        StaffAssistedOfferResponseController.class,
        StaffAssistedContractAcknowledgmentController.class,
        StaffAssistedActionEvidenceController.class
})
@Import({
        SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class
})
class StaffAssistedDownstreamSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean QueryAssistedApprovedOfferResponseUseCase queryOffer;
    @MockitoBean RecordAssistedApprovedOfferResponseUseCase recordOffer;
    @MockitoBean RecordAssistedLoanContractAcknowledgmentUseCase recordAcknowledgment;
    @MockitoBean ManageAssistedActionEvidenceUseCase evidence;
    @MockitoBean LoanContractMapper mapper;

    private final UUID applicationId = UUID.randomUUID();

    @Test
    void offerReadAndCommandRequireExactStaffOfferAuthority() throws Exception {
        when(recordOffer.record(any())).thenReturn(new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.SUCCESS, null));
        String path = "/api/v1/staff/loan-applications/{id}/offer-response";
        String body = """
                {"requestId":"%s","expectedApprovedOfferId":"%s", "action":"ACCEPT",
                "evidenceDocumentVersionId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(get(path, applicationId)).andExpect(status().isUnauthorized());
        for (String nearMiss : new String[]{
                "loan:offer:respond:own", "loan:read", "loan:offer:respond:staff:extra"
        }) {
            mockMvc.perform(post(path, applicationId)
                            .with(authority(nearMiss)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(path, applicationId)
                        .with(authority("loan:offer:respond:staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void contractAcknowledgmentRequiresExactStaffContractAuthority() throws Exception {
        String path = "/api/v1/staff/loan-applications/{id}/contract/acknowledgment";
        String body = """
                {"acknowledgmentRequestId":"%s","contractId":"%s", "expectedContractVersion":1,
                "evidenceDocumentVersionId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(path, applicationId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (String nearMiss : new String[]{
                "loan:contract:acknowledge:own", "loan:contract:read",
                "loan:contract:acknowledge:staff:extra"
        }) {
            mockMvc.perform(post(path, applicationId)
                            .with(authority(nearMiss)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(path, applicationId)
                        .with(authority("loan:contract:acknowledge:staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void evidenceUploadRequiresExactPurposeSpecificDocumentAuthority() throws Exception {
        String path = "/api/v1/staff/loan-applications/{id}/assisted-action-evidence/"
                + "CUSTOMER_OFFER_RESPONSE/versions";
        MockMultipartFile file = new MockMultipartFile(
                "file", "signed.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(multipart(path, applicationId).file(file)
                        .param("approvedOfferId", UUID.randomUUID().toString())
                        .param("declaredOfferDecision", "ACCEPT")
                        .param("uploadRequestId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(path, applicationId).file(file)
                        .param("approvedOfferId", UUID.randomUUID().toString())
                        .param("declaredOfferDecision", "ACCEPT")
                        .param("uploadRequestId", UUID.randomUUID().toString())
                        .with(authority("document:upload:assisted")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(path, applicationId).file(file)
                        .param("approvedOfferId", UUID.randomUUID().toString())
                        .param("declaredOfferDecision", "ACCEPT")
                        .param("uploadRequestId", UUID.randomUUID().toString())
                        .with(authority("document:upload:assisted-action")))
                .andExpect(status().isOk());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String value
    ) {
        return user("staff").authorities(new SimpleGrantedAuthority(value));
    }
}

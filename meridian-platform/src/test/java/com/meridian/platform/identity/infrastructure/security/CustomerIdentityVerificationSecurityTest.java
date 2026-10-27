package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.customer.application.port.in.CustomerIdentityVerificationUseCase;
import com.meridian.platform.customer.application.dto.CustomerIdentityContent;
import com.meridian.platform.customer.infrastructure.adapter.in.web.CustomerIdentityVerificationController;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerIdentityVerificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class})
class CustomerIdentityVerificationSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerIdentityVerificationUseCase useCase;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    private static final String BASE = "/api/v1/staff/customer-identity-verifications";
    @Test void anonymousGenericPermissionsAndUnrelatedRoleBundlesCannotReachReview() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        for (String permission : List.of("customer:read", "customer:intake:manage", "document:review", "approval:decide", "loan:read", "loan:disburse", "identity:user:manage", "customer:identity:read:own")) {
            var actor = user("actor").authorities(new SimpleGrantedAuthority(permission));
            mvc.perform(get(BASE).with(actor)).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/" + UUID.randomUUID()).with(actor)).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/" + UUID.randomUUID() + "/content").with(actor)).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/verify").with(actor).contentType("application/json").content("{\"requestId\":\"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa\",\"documentVersionId\":\"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb\",\"presentedIdentityReference\":\"FICTIONAL\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }
    @Test void exactReviewerAuthorityAllowsQueueAndContentHasPrivateHeaders() throws Exception {
        when(useCase.pending(0, 25)).thenReturn(List.of());
        var reviewer = user("reviewer").authorities(new SimpleGrantedAuthority("customer:identity:verify"));
        mvc.perform(get(BASE).with(reviewer)).andExpect(status().isOk()).andExpect(content().json("[]"));
        UUID id = UUID.randomUUID();
        when(useCase.readStaff(id)).thenReturn(new CustomerIdentityContent("identity.pdf", "application/pdf", 4, new ByteArrayInputStream(new byte[]{1,2,3,4})));
        mvc.perform(get(BASE + "/" + id + "/content").with(reviewer)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contentBytesMetadataUtf8FilenameAndStreamLifecycleArePreserved(boolean staff) throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[]{0, 1, 2, 3, (byte) 0xff};
        var stream = spy(new ByteArrayInputStream(bytes));
        var result = new CustomerIdentityContent("hồ sơ.pdf", "application/pdf", bytes.length, stream);
        if (staff) when(useCase.readStaff(id)).thenReturn(result);
        else when(useCase.readOwn(id)).thenReturn(result);
        var actor = user("reader").authorities(new SimpleGrantedAuthority(
                staff ? "customer:identity:verify" : "customer:identity:read:own"));

        mvc.perform(get(contentPath(staff, id)).with(actor)).andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Length", Integer.toString(bytes.length)))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''h%E1%BB%93%20s%C6%A1.pdf")))
                .andExpect(response -> assertEquals("hồ sơ.pdf", org.springframework.http.ContentDisposition
                        .parse(response.getResponse().getHeader("Content-Disposition")).getFilename()));
        verify(stream).close();
        if (staff) { verify(useCase).readStaff(id); verify(useCase, never()).readOwn(any()); }
        else { verify(useCase).readOwn(id); verify(useCase, never()).readStaff(any()); }
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contentRequiresExactAuthorityBeforeCallingTheUseCase(boolean staff) throws Exception {
        String path = contentPath(staff, UUID.randomUUID());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String permission : List.of("customer:read", "document:read:own", "document:review",
                staff ? "customer:identity:read:own" : "customer:identity:verify")) {
            mvc.perform(get(path).with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contentNotFoundPreservesTheConcealedHttpError(boolean staff) throws Exception {
        UUID id = UUID.randomUUID();
        var missing = new EntityNotFoundException("IDENTITY_VERIFICATION_NOT_FOUND", "Identity verification was not found.");
        if (staff) when(useCase.readStaff(id)).thenThrow(missing);
        else when(useCase.readOwn(id)).thenThrow(missing);
        mvc.perform(get(contentPath(staff, id)).with(user("reader").authorities(new SimpleGrantedAuthority(
                        staff ? "customer:identity:verify" : "customer:identity:read:own"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_VERIFICATION_NOT_FOUND"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }
    private String contentPath(boolean staff, UUID id) {
        return (staff ? BASE : "/api/v1/customers/me/identity-verifications") + "/" + id + "/content";
    }
    @Test void ownUploadAndExactIntakeBindingReturnCreated() throws Exception {
        mvc.perform(multipart("/api/v1/customers/me/identity-verifications")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "fictional.pdf", "application/pdf", "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("uploadRequestId", UUID.randomUUID().toString())
                .with(user("customer").authorities(new SimpleGrantedAuthority("customer:identity:write:own"))))
                .andExpect(status().isCreated());
        UUID caseId = UUID.randomUUID(); UUID versionId = UUID.randomUUID();
        mvc.perform(post("/api/v1/staff/assisted-originations/" + caseId + "/identity-verifications")
                .with(user("officer").authorities(new SimpleGrantedAuthority("customer:identity:verify")))
                .contentType("application/json").content("{\"documentVersionId\":\"" + versionId + "\"}"))
                .andExpect(status().isCreated());
        verify(useCase).submitIntake(caseId, versionId);
    }
    @Test void oldOwnDocumentPermissionsDoNotAuthorizeIdentityWorkspace() throws Exception {
        for (String permission : List.of("document:upload:own", "document:read:own", "customer:read:own", "customer:identity:verify"))
            mvc.perform(get("/api/v1/customers/me/identity-verifications").with(user("actor").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }
}

package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.service.IntakeEvidenceService;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.infrastructure.adapter.in.web.StaffIntakeEvidenceController;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffIntakeEvidenceController.class)
@Import({IntakeEvidenceService.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class, MeridianAuthenticationEntryPoint.class, MeridianAccessDeniedHandler.class})
class IntakeEvidenceContentSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean IntakeDocumentRepository documents;
    @MockitoBean DocumentStoragePort storage;
    @MockitoBean LoanAssistedOriginationPort cases;
    @MockitoBean CurrentUserProvider users;
    @MockitoBean BusinessAuditPublisher audits;
    @MockitoBean Clock clock;
    private final UUID caseId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final String path = "/api/v1/staff/assisted-originations/" + caseId
            + "/evidence/UCL_PAPER_APPLICATION/versions/" + versionId + "/content";

    @BeforeEach
    void setUp() {
        UUID documentId = UUID.randomUUID();
        var now = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(users.currentUser()).thenReturn(actor("STAFF", null));
        when(cases.authorizeRead(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(documents.findByCaseAndType(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION)).thenReturn(Optional.of(
                new IntakeDocument(documentId, caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId, now, now)));
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(new IntakeDocumentVersion(
                versionId, documentId, 1, UUID.randomUUID(), null, "signed paper.pdf", "application/pdf",
                "application/pdf", 5, "a".repeat(64), "private/opaque-key", UUID.randomUUID(), now)));
        when(storage.open("private/opaque-key")).thenAnswer(call -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));
    }

    @Test
    void authorizedReadHasExactBytesMetadataAndHardenedHeaders() throws Exception {
        mvc.perform(get(path).with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3, 4, 5}))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().longValue("Content-Length", 5))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("signed%20paper.pdf")))
                .andExpect(header().string("Content-Disposition", not(containsString("opaque-key"))))
                .andExpect(header().string("Cache-Control", allOf(containsString("no-store"), containsString("private"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verifyNoInteractions(audits);
    }

    @Test
    void anonymousReadIsDenied() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(cases, documents, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loan:read", "document:review", "approval:decide", "document:read:own", "document:upload:staff"})
    void otherAuthoritiesCannotReadBytes(String permission) throws Exception {
        mvc.perform(get(path).with(user("caller").authorities(new SimpleGrantedAuthority(permission))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cases, documents, storage);
    }

    @Test
    void customerCannotUseStaffEndpointEvenWithIntakePermission() throws Exception {
        when(users.currentUser()).thenReturn(actor("CUSTOMER", UUID.randomUUID()));
        mvc.perform(get(path).with(user("customer").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("INTAKE_EVIDENCE_ACCESS_DENIED"));
        verifyNoInteractions(cases, documents, storage);
    }

    @Test
    void exactIntakePermissionStillRequiresLoanCaseAuthority() throws Exception {
        when(cases.authorizeRead(caseId)).thenThrow(new AuthorizationException(
                "ASSISTED_ORIGINATION_ACCESS_DENIED", "Staff-assisted origination access is denied."));
        mvc.perform(get(path).with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ASSISTED_ORIGINATION_ACCESS_DENIED"));
        verifyNoInteractions(documents, storage);
    }

    @Test
    void unavailableStoredObjectHasSafeAvailabilityError() throws Exception {
        when(storage.open("private/opaque-key")).thenThrow(new ServiceUnavailableException(
                "DOCUMENT_STORAGE_UNAVAILABLE", "Document storage is temporarily unavailable."));
        mvc.perform(get(path).with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("DOCUMENT_STORAGE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("opaque-key"))));
        verifyNoInteractions(audits);
    }

    @Test
    void configuredFrontendCanReadAttachmentFilenameWithoutBroadeningOrigins() throws Exception {
        mvc.perform(get(path).header("Origin", "http://localhost:5174")
                        .with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        mvc.perform(get(path).header("Origin", "https://untrusted.invalid")
                        .with(user("staff").authorities(new SimpleGrantedAuthority("document:upload:intake"))))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedUser actor(String type, UUID customerId) {
        return new AuthenticatedUser(UUID.randomUUID(), "caller@meridian.local", type, customerId,
                Set.of(), Set.of("document:upload:intake"));
    }
}

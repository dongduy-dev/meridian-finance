package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.port.in.QueryDocumentReviewQueueUseCase;
import com.meridian.platform.document.application.port.in.ReadDocumentContentUseCase;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.application.port.in.QueryStaffDocumentChecklistUseCase;
import com.meridian.platform.document.infrastructure.adapter.in.web.CustomerDocumentController;
import com.meridian.platform.document.infrastructure.adapter.in.web.DocumentContentController;
import com.meridian.platform.document.infrastructure.adapter.in.web.DocumentReviewController;
import com.meridian.platform.document.infrastructure.adapter.in.web.DocumentReviewQueueController;
import com.meridian.platform.document.infrastructure.adapter.in.web.StaffDocumentController;
import com.meridian.platform.document.infrastructure.adapter.in.web.StaffDocumentReadController;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.CompleteStaffCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitStaffCorrectionUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionCaseUseCase;
import com.meridian.platform.loan.infrastructure.adapter.in.web.CustomerCorrectionController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffCorrectionController;
import com.meridian.platform.loan.infrastructure.adapter.in.web.StaffCorrectionReadController;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CustomerDocumentController.class,
        StaffDocumentController.class,
        DocumentContentController.class,
        DocumentReviewQueueController.class,
        DocumentReviewController.class,
        StaffDocumentReadController.class,
        CustomerCorrectionController.class,
        StaffCorrectionController.class,
        StaffCorrectionReadController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        MeridianAuthenticationEntryPoint.class,
        MeridianAccessDeniedHandler.class
})
class DocumentCorrectionSecurityTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ITEM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID VERSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TASK_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID CUSTOMER_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository accessTokenRevocationRepository;
    @MockitoBean private UploadDocumentUseCase uploadDocumentUseCase;
    @MockitoBean private QueryDocumentReviewQueueUseCase queryDocumentReviewQueueUseCase;
    @MockitoBean private ReviewDocumentUseCase reviewDocumentUseCase;
    @MockitoBean private ReadDocumentContentUseCase readDocumentContentUseCase;
    @MockitoBean private QueryStaffDocumentChecklistUseCase queryStaffDocumentChecklistUseCase;
    @MockitoBean private QueryOwnCorrectionTasksUseCase queryOwnCorrectionTasksUseCase;
    @MockitoBean private CompleteOwnCorrectionTaskUseCase completeOwnCorrectionTaskUseCase;
    @MockitoBean private ResubmitOwnCorrectionUseCase resubmitOwnCorrectionUseCase;
    @MockitoBean private QueryStaffCorrectionTasksUseCase queryStaffCorrectionTasksUseCase;
    @MockitoBean private CompleteStaffCorrectionTaskUseCase completeStaffCorrectionTaskUseCase;
    @MockitoBean private ResubmitStaffCorrectionUseCase resubmitStaffCorrectionUseCase;
    @MockitoBean private QueryStaffCorrectionCaseUseCase queryStaffCorrectionCaseUseCase;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    @Test
    void rejectsAnonymousDocumentAndCorrectionAccess() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/{id}/corrections/tasks", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff-corrections/tasks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/document-review-items"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/documents", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/corrections", APPLICATION_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{id}/documents/{item}/versions/{version}/content",
                        APPLICATION_ID, ITEM_ID, VERSION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedActorsWithoutEachEndpointPermission() throws Exception {
        var actor = user("authenticated").authorities(new SimpleGrantedAuthority("loan:submit"));

        mockMvc.perform(get("/api/v1/loan-applications/{id}/corrections/tasks", APPLICATION_ID).with(actor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/staff-corrections/tasks").with(actor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/document-review-items").with(actor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/documents", APPLICATION_ID).with(actor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/corrections", APPLICATION_ID).with(actor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(
                        "/api/v1/loan-applications/{id}/documents/{item}/versions/{version}/content",
                        APPLICATION_ID, ITEM_ID, VERSION_ID).with(actor))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAudienceSpecificTaskQueuesAndNoStoreContentRead() throws Exception {
        when(queryOwnCorrectionTasksUseCase.findOwnTasks(APPLICATION_ID)).thenReturn(List.of());
        when(queryStaffCorrectionTasksUseCase.findStaffTasks(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());
        when(queryDocumentReviewQueueUseCase.findAwaitingReview(0, 20)).thenReturn(List.of());
        when(readDocumentContentUseCase.read(APPLICATION_ID, ITEM_ID, VERSION_ID))
                .thenReturn(new DocumentContentDto(
                        "safe.pdf", "application/pdf", 4,
                        new ByteArrayInputStream(new byte[]{1, 2, 3, 4})
                ));

        mockMvc.perform(get("/api/v1/loan-applications/{id}/corrections/tasks", APPLICATION_ID)
                        .with(user("customer").authorities(
                                new SimpleGrantedAuthority("loan:correction:own"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff-corrections/tasks")
                        .with(user("staff").authorities(
                                new SimpleGrantedAuthority("loan:correction:staff"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/document-review-items")
                        .with(user("reviewer").authorities(
                                new SimpleGrantedAuthority("document:review"))))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{id}/documents/{item}/versions/{version}/content",
                        APPLICATION_ID, ITEM_ID, VERSION_ID)
                        .with(user("reviewer").authorities(
                                new SimpleGrantedAuthority("document:review"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void allowsOnlyAuthorizedCustomerAndStaffUploads() throws Exception {
        when(currentUserProvider.currentUser()).thenReturn(authenticatedCustomer());
        when(uploadDocumentUseCase.upload(any())).thenReturn(versionDto());

        MockMultipartHttpServletRequestBuilder customerUpload = multipart(
                "/api/v1/loan-applications/{id}/documents/{item}/versions", APPLICATION_ID, ITEM_ID);
        mockMvc.perform(customerUpload
                        .file("file", new byte[]{1, 2, 3})
                        .param("uploadRequestId", UUID.randomUUID().toString())
                        .with(user("customer").authorities(
                                new SimpleGrantedAuthority("document:upload:own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentVersionId").value(VERSION_ID.toString()));

        when(currentUserProvider.currentUser()).thenReturn(authenticatedStaff());
        MockMultipartHttpServletRequestBuilder staffUpload = multipart(
                "/api/v1/staff/loan-applications/{id}/documents/{item}/versions", APPLICATION_ID, ITEM_ID);
        mockMvc.perform(staffUpload
                        .file("file", new byte[]{1, 2, 3})
                        .param("uploadRequestId", UUID.randomUUID().toString())
                        .with(user("staff").authorities(
                                new SimpleGrantedAuthority("document:upload:staff"))))
                .andExpect(status().isOk());
    }

    @Test
    void grantsNewStaffReadsOnlyToTheirExactPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/documents", APPLICATION_ID)
                        .with(user("reviewer").authorities(
                                new SimpleGrantedAuthority("document:review"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/documents", APPLICATION_ID)
                        .with(user("customer").authorities(
                                new SimpleGrantedAuthority("document:read:own"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/corrections", APPLICATION_ID)
                        .with(user("correction-staff").authorities(
                                new SimpleGrantedAuthority("loan:correction:staff"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/loan-applications/{id}/corrections", APPLICATION_ID)
                        .with(user("reader").authorities(
                                new SimpleGrantedAuthority("loan:read"))))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedUser authenticatedCustomer() {
        return new AuthenticatedUser(
                USER_ID, "customer@meridian.test", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("document:upload:own")
        );
    }

    private AuthenticatedUser authenticatedStaff() {
        return new AuthenticatedUser(
                USER_ID, "staff@meridian.test", "STAFF", null,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("document:upload:staff")
        );
    }

    private DocumentVersionDto versionDto() {
        return new DocumentVersionDto(
                VERSION_ID, ITEM_ID, 1, "safe.pdf", "application/pdf", 3, LocalDateTime.now()
        );
    }
}

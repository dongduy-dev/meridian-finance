package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.port.in.ReadDocumentContentUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentContentControllerTest {

    @Test
    void customerEndpointRejectsStaffEvenWhenBothOwnAndReviewAuthoritiesArePresent() throws Exception {
        var workflow = org.mockito.Mockito.mock(com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort.class);
        var checklists = org.mockito.Mockito.mock(com.meridian.platform.document.application.port.out.DocumentChecklistRepository.class);
        var documents = org.mockito.Mockito.mock(com.meridian.platform.document.application.port.out.DocumentRepository.class);
        var storage = org.mockito.Mockito.mock(com.meridian.platform.document.application.port.out.DocumentStoragePort.class);
        var users = org.mockito.Mockito.mock(com.meridian.platform.shared.application.security.CurrentUserProvider.class);
        org.mockito.Mockito.when(users.currentUser()).thenReturn(new com.meridian.platform.shared.application.security.AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.local", "STAFF", null, java.util.Set.of(),
                java.util.Set.of("document:review", "document:read:own")));
        var service = new com.meridian.platform.document.application.service.ReadDocumentContentService(workflow, checklists, documents, storage, users);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentContentController(service))
                .setControllerAdvice(new com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/loan-applications/{applicationId}/documents/{itemId}/versions/{versionId}/content",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(workflow, checklists, documents, storage);
    }

    @Test
    void returnsAttachmentUsingDetectedMimeAndPrivateNoStoreHeaders() throws Exception {
        ReadDocumentContentUseCase useCase = org.mockito.Mockito.mock(ReadDocumentContentUseCase.class);
        org.mockito.Mockito.when(useCase.read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> new DocumentContentDto(
                        "recent-payslip.pdf",
                        "application/pdf",
                        5,
                        new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5})
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new DocumentContentController(useCase)
        ).build();

        mockMvc.perform(get(
                        "/api/v1/loan-applications/{applicationId}/documents/{itemId}/versions/{versionId}/content",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4, 5}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")
                ))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("storage"))
                ));
    }
}

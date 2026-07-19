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
    void returnsAttachmentUsingDetectedMimeAndPrivateNoStoreHeaders() throws Exception {
        ReadDocumentContentUseCase useCase = (applicationId, itemId, versionId) ->
                new DocumentContentDto(
                        "recent-payslip.pdf",
                        "application/pdf",
                        5,
                        new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5})
                );
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

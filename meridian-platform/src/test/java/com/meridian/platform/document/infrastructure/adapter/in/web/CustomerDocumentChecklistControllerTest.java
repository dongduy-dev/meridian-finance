package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.CustomerDocumentChecklistDto;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerDocumentChecklistControllerTest {

    @Test
    void exposesSafeChecklistAndCurrentVersionWithoutRestrictedReviewOrStorageEvidence() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CustomerDocumentChecklistDto dto = new CustomerDocumentChecklistDto(
                UUID.randomUUID(), applicationId, "SUBMISSION", true, false,
                List.of(new CustomerDocumentChecklistDto.ChecklistItemDto(
                        itemId, "INCOME_PROOF", "REQUIRED", "AWAITING_REVIEW",
                        true, false, new DocumentVersionDto(
                        UUID.randomUUID(), itemId, 1, "income.pdf", "application/pdf",
                        2048, LocalDateTime.of(2026, 8, 30, 10, 0)
                )))
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomerDocumentChecklistController(
                ignored -> dto
        )).build();

        mvc.perform(get("/api/v1/loan-applications/{loanApplicationId}/documents", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanApplicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.items[0].customerStatus").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.items[0].currentVersion.originalFilename").value("income.pdf"))
                .andExpect(jsonPath("$.items[0].currentReviewDecisionId").doesNotExist())
                .andExpect(jsonPath("$.items[0].restrictedStaffNotes").doesNotExist())
                .andExpect(jsonPath("$.items[0].currentVersion.storageKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].currentVersion.sha256Hex").doesNotExist());
    }
}

package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.CustomerDocumentChecklistDto;
import com.meridian.platform.document.application.port.in.QueryOwnDocumentChecklistUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/documents")
public class CustomerDocumentChecklistController {

    private final QueryOwnDocumentChecklistUseCase queryChecklist;

    public CustomerDocumentChecklistController(QueryOwnDocumentChecklistUseCase queryChecklist) {
        this.queryChecklist = queryChecklist;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('document:read:own')")
    public CustomerDocumentChecklistDto query(@PathVariable UUID loanApplicationId) {
        return queryChecklist.query(loanApplicationId);
    }
}

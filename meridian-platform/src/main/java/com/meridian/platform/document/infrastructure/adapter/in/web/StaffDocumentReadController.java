package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.StaffDocumentChecklistDto;
import com.meridian.platform.document.application.port.in.QueryStaffDocumentChecklistUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/documents")
public class StaffDocumentReadController {
    private final QueryStaffDocumentChecklistUseCase queryChecklist;

    public StaffDocumentReadController(QueryStaffDocumentChecklistUseCase queryChecklist) {
        this.queryChecklist = queryChecklist;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('document:review', 'approval:decide')")
    public ResponseEntity<StaffDocumentChecklistDto> query(@PathVariable UUID loanApplicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(queryChecklist.query(loanApplicationId));
    }
}

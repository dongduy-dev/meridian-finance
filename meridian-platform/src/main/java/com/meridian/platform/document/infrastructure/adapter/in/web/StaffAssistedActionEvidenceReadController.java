package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.StaffAssistedActionEvidenceDto;
import com.meridian.platform.document.application.port.in.ReadStaffAssistedActionEvidenceUseCase;
import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/assisted-action-evidence")
@PreAuthorize("hasAnyAuthority('document:review', 'loan:offer:respond:staff', 'loan:contract:read', 'loan:correction:staff')")
public class StaffAssistedActionEvidenceReadController {
    private final ReadStaffAssistedActionEvidenceUseCase reads;

    public StaffAssistedActionEvidenceReadController(ReadStaffAssistedActionEvidenceUseCase reads) {
        this.reads = reads;
    }

    @GetMapping
    public ResponseEntity<List<StaffAssistedActionEvidenceDto>> query(@PathVariable UUID loanApplicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(reads.query(loanApplicationId));
    }

    @GetMapping("/{evidenceType}/versions/{documentVersionId}/content")
    public ResponseEntity<InputStreamResource> read(@PathVariable UUID loanApplicationId,
            @PathVariable AssistedActionEvidenceType evidenceType, @PathVariable UUID documentVersionId) {
        return DocumentContentController.response(reads.read(loanApplicationId, evidenceType, documentVersionId));
    }
}

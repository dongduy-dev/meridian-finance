package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.IntakeOcrJobDto;
import com.meridian.platform.document.application.port.in.ManageIntakeOcrUseCase;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/assisted-originations/{caseId}/evidence/{evidenceType}/versions/{versionId}/ocr")
@PreAuthorize("hasAuthority('document:upload:intake')")
public class StaffIntakeOcrController {

    private final ManageIntakeOcrUseCase useCase;

    public StaffIntakeOcrController(ManageIntakeOcrUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public IntakeOcrJobDto start(
            @PathVariable UUID caseId,
            @PathVariable IntakeEvidenceType evidenceType,
            @PathVariable UUID versionId
    ) {
        return useCase.start(caseId, evidenceType, versionId);
    }

    @GetMapping
    public IntakeOcrJobDto getStatus(
            @PathVariable UUID caseId,
            @PathVariable IntakeEvidenceType evidenceType,
            @PathVariable UUID versionId
    ) {
        return useCase.getStatus(caseId, evidenceType, versionId);
    }
}

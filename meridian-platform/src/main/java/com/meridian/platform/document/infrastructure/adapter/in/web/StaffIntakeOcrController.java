package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.IntakeOcrJobDto;
import com.meridian.platform.document.application.dto.FinalizeIntakeOcrReviewRequest;
import com.meridian.platform.document.application.dto.IntakeOcrReviewDto;
import com.meridian.platform.document.application.port.in.ManageIntakeOcrUseCase;
import com.meridian.platform.document.application.port.in.ManageIntakeOcrReviewUseCase;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/assisted-originations/{caseId}/evidence/{evidenceType}/versions/{versionId}/ocr")
@PreAuthorize("hasAuthority('document:upload:intake')")
public class StaffIntakeOcrController {

    private final ManageIntakeOcrUseCase useCase;
    private final ManageIntakeOcrReviewUseCase reviewUseCase;

    public StaffIntakeOcrController(
            ManageIntakeOcrUseCase useCase,
            ManageIntakeOcrReviewUseCase reviewUseCase
    ) {
        this.useCase = useCase;
        this.reviewUseCase = reviewUseCase;
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

    @GetMapping("/review")
    public IntakeOcrReviewDto getReview(
            @PathVariable UUID caseId,
            @PathVariable IntakeEvidenceType evidenceType,
            @PathVariable UUID versionId
    ) {
        return reviewUseCase.get(caseId, evidenceType, versionId);
    }

    @PostMapping("/review")
    public IntakeOcrReviewDto finalizeReview(
            @PathVariable UUID caseId,
            @PathVariable IntakeEvidenceType evidenceType,
            @PathVariable UUID versionId,
            @Valid @RequestBody FinalizeIntakeOcrReviewRequest request
    ) {
        return reviewUseCase.finalizeReview(caseId, evidenceType, versionId, request);
    }
}

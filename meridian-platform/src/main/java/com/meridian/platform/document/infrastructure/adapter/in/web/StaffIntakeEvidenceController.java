package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.IntakeEvidenceDto;
import com.meridian.platform.document.application.dto.IntakeEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.application.port.in.ManageIntakeEvidenceUseCase;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/assisted-originations/{caseId}/evidence")
@PreAuthorize("hasAuthority('document:upload:intake')")
public class StaffIntakeEvidenceController {

    private final ManageIntakeEvidenceUseCase useCase;

    public StaffIntakeEvidenceController(ManageIntakeEvidenceUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<IntakeEvidenceDto> find(@PathVariable UUID caseId) {
        return useCase.findEvidence(caseId);
    }

    @PostMapping(value = "/{evidenceType}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntakeEvidenceVersionDto upload(
            @PathVariable UUID caseId,
            @PathVariable IntakeEvidenceType evidenceType,
            @RequestParam UUID uploadRequestId,
            @RequestParam(required = false) UUID expectedCurrentVersionId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return useCase.upload(new UploadIntakeEvidenceCommand(
                    caseId, evidenceType, uploadRequestId, expectedCurrentVersionId,
                    file.getOriginalFilename(), file.getContentType(), file.getInputStream()
            ));
        } catch (IOException exception) {
            throw new ServiceUnavailableException("DOCUMENT_STORAGE_UNAVAILABLE",
                    "Document upload stream could not be opened.");
        }
    }
}

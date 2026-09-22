package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.AssistedActionEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadAssistedActionEvidenceCommand;
import com.meridian.platform.document.application.port.in.ManageAssistedActionEvidenceUseCase;
import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;
import com.meridian.platform.document.domain.model.AssistedOfferDecision;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/assisted-action-evidence")
@PreAuthorize("hasAuthority('document:upload:assisted-action')")
public class StaffAssistedActionEvidenceController {

    private final ManageAssistedActionEvidenceUseCase useCase;

    public StaffAssistedActionEvidenceController(ManageAssistedActionEvidenceUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(value = "/{evidenceType}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssistedActionEvidenceVersionDto upload(
            @PathVariable UUID loanApplicationId,
            @PathVariable AssistedActionEvidenceType evidenceType,
            @RequestParam(required = false) UUID approvedOfferId,
            @RequestParam(required = false) AssistedOfferDecision declaredOfferDecision,
            @RequestParam(required = false) UUID loanContractId,
            @RequestParam(required = false) Integer contractVersion,
            @RequestParam(required = false) UUID correctionRequestId,
            @RequestParam UUID uploadRequestId,
            @RequestParam(required = false) UUID expectedCurrentVersionId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return useCase.upload(new UploadAssistedActionEvidenceCommand(
                    loanApplicationId, evidenceType, approvedOfferId, declaredOfferDecision,
                    loanContractId, contractVersion, correctionRequestId,
                    uploadRequestId, expectedCurrentVersionId,
                    file.getOriginalFilename(), file.getContentType(), file.getInputStream()));
        } catch (IOException exception) {
            throw new ServiceUnavailableException(
                    "DOCUMENT_STORAGE_UNAVAILABLE", "Document upload stream could not be opened.");
        }
    }
}

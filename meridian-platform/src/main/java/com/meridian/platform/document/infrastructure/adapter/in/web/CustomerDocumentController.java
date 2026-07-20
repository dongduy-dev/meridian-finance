package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/documents")
public class CustomerDocumentController {
    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public CustomerDocumentController(
            UploadDocumentUseCase uploadDocumentUseCase,
            CurrentUserProvider currentUserProvider
    ) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(value = "/{checklistItemId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('document:upload:own')")
    public DocumentVersionDto uploadOwnDocument(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID checklistItemId,
            @RequestParam UUID uploadRequestId,
            @RequestParam(required = false) UUID expectedCurrentVersionId,
            @RequestParam("file") MultipartFile file
    ) {
        AuthenticatedUser user = currentUserProvider.currentUser();
        try {
            return uploadDocumentUseCase.upload(new UploadDocumentCommand(
                    loanApplicationId,
                    checklistItemId,
                    uploadRequestId,
                    expectedCurrentVersionId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    DocumentUploaderActorType.CUSTOMER,
                    user.userId(),
                    user.requireCustomerId()
            ));
        } catch (IOException exception) {
            throw new ServiceUnavailableException(
                    "DOCUMENT_STORAGE_UNAVAILABLE",
                    "Document upload stream could not be opened."
            );
        }
    }
}

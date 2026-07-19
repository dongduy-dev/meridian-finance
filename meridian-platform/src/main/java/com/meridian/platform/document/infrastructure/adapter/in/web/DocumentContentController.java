package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.port.in.ReadDocumentContentUseCase;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class DocumentContentController {

    private final ReadDocumentContentUseCase readDocumentContentUseCase;

    public DocumentContentController(ReadDocumentContentUseCase readDocumentContentUseCase) {
        this.readDocumentContentUseCase = readDocumentContentUseCase;
    }

    @GetMapping("/api/v1/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content")
    @PreAuthorize("hasAuthority('document:read:own')")
    public ResponseEntity<InputStreamResource> readOwnDocument(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID checklistItemId,
            @PathVariable UUID documentVersionId
    ) {
        return response(readDocumentContentUseCase.read(
                loanApplicationId, checklistItemId, documentVersionId));
    }

    @GetMapping("/api/v1/staff/loan-applications/{loanApplicationId}/documents/{checklistItemId}/versions/{documentVersionId}/content")
    @PreAuthorize("hasAuthority('document:review')")
    public ResponseEntity<InputStreamResource> readStaffDocument(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID checklistItemId,
            @PathVariable UUID documentVersionId
    ) {
        return response(readDocumentContentUseCase.read(
                loanApplicationId, checklistItemId, documentVersionId));
    }

    private ResponseEntity<InputStreamResource> response(DocumentContentDto document) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.detectedMimeType()))
                .contentLength(document.byteSize())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(document.content()));
    }
}

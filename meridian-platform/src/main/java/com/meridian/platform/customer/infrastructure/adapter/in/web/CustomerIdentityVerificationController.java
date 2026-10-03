package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.*;
import com.meridian.platform.customer.application.port.in.CustomerIdentityVerificationUseCase;
import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class CustomerIdentityVerificationController {
    private final CustomerIdentityVerificationUseCase useCase;
    public CustomerIdentityVerificationController(CustomerIdentityVerificationUseCase useCase) { this.useCase = useCase; }
    @GetMapping("/api/v1/customers/me/identity-verifications")
    @PreAuthorize("hasAuthority('customer:identity:read:own')")
    public List<CustomerIdentityVerificationDto> own() { return useCase.ownHistory(); }
    @PostMapping(value = "/api/v1/customers/me/identity-verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:identity:write:own')")
    public CustomerIdentityVerificationDto upload(@RequestParam UUID uploadRequestId, @RequestParam(required = false) UUID expectedCurrentVersionId,
            @RequestPart MultipartFile file) throws IOException {
        try (var stream = file.getInputStream()) {
            return useCase.submitOwn(uploadRequestId, expectedCurrentVersionId, stream, file.getContentType(), file.getOriginalFilename());
        }
    }
    public record IntakeRequest(@NotNull UUID documentVersionId) {}
    @PostMapping("/api/v1/staff/assisted-originations/{caseId}/identity-verifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public CustomerIdentityVerificationDto intake(@PathVariable UUID caseId, @Valid @RequestBody IntakeRequest request) { return useCase.submitIntake(caseId, request.documentVersionId()); }
    @GetMapping("/api/v1/staff/customer-identity-verifications")
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public List<CustomerIdentityVerificationDto> queue(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) { return useCase.pending(page, size); }
    @GetMapping("/api/v1/staff/customer-identity-verifications/{id}")
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public CustomerIdentityVerificationDto detail(@PathVariable UUID id) { return useCase.detail(id); }
    @PostMapping("/api/v1/staff/customer-identity-verifications/{id}/verify")
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public CustomerIdentityVerificationDto verify(@PathVariable UUID id, @Valid @RequestBody CustomerIdentityDecisionRequest request) { return useCase.decide(id, true, request); }
    @PostMapping("/api/v1/staff/customer-identity-verifications/{id}/reject")
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public CustomerIdentityVerificationDto reject(@PathVariable UUID id, @Valid @RequestBody CustomerIdentityDecisionRequest request) { return useCase.decide(id, false, request); }
    @GetMapping("/api/v1/customers/me/identity-verifications/{id}/content")
    @PreAuthorize("hasAuthority('customer:identity:read:own')")
    public ResponseEntity<InputStreamResource> ownContent(@PathVariable UUID id) { return content(useCase.readOwn(id)); }
    @GetMapping("/api/v1/staff/customer-identity-verifications/{id}/content")
    @PreAuthorize("hasAuthority('customer:identity:verify')")
    public ResponseEntity<InputStreamResource> staffContent(@PathVariable UUID id) { return content(useCase.readStaff(id)); }
    private ResponseEntity<InputStreamResource> content(CustomerIdentityEvidencePort.Content c) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mimeType())).contentLength(c.byteSize())
                .cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(c.filename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(c.content()));
    }
}

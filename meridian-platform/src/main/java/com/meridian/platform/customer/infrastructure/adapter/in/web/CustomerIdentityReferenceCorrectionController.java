package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.CorrectIdentityReferenceRequest;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.port.in.CorrectCustomerIdentityReferenceUseCase;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class CustomerIdentityReferenceCorrectionController {
    private final CorrectCustomerIdentityReferenceUseCase useCase;
    public CustomerIdentityReferenceCorrectionController(CorrectCustomerIdentityReferenceUseCase useCase) { this.useCase = useCase; }

    @PutMapping("/api/v1/customers/me/identity-reference")
    @PreAuthorize("hasAuthority('customer:profile:write:own')")
    public ResponseEntity<CustomerDto> own(@Valid @RequestBody CorrectIdentityReferenceRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(useCase.correctOwn(request));
    }

    @PutMapping("/api/v1/staff/customers/{customerId}/identity-reference")
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public ResponseEntity<CustomerDto> staff(@PathVariable UUID customerId, @Valid @RequestBody CorrectIdentityReferenceRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(useCase.correctForIntake(customerId, request));
    }
}

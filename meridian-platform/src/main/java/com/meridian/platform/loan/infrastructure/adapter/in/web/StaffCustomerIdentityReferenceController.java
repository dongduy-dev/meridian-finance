package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerIdentityReferenceRevealDto;
import com.meridian.platform.loan.application.port.in.RevealStaffCustomerIdentityReferenceUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/customer-identity-reference")
public class StaffCustomerIdentityReferenceController {
    private final RevealStaffCustomerIdentityReferenceUseCase revealIdentity;

    public StaffCustomerIdentityReferenceController(RevealStaffCustomerIdentityReferenceUseCase revealIdentity) {
        this.revealIdentity = revealIdentity;
    }

    @PostMapping("/reveal")
    @PreAuthorize("hasAuthority('loan:read') and hasAuthority('customer:read') and hasAuthority('customer:identity:reveal')")
    public ResponseEntity<CustomerIdentityReferenceRevealDto> reveal(@PathVariable UUID loanApplicationId) {
        var result = revealIdentity.reveal(loanApplicationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(new CustomerIdentityReferenceRevealDto(result.loanApplicationId(), result.identityReference()));
    }
}

package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies/{partnerCompanyId}/employee-verifications")
public class PartnerEmployeeVerificationController {

    private final VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase;

    public PartnerEmployeeVerificationController(VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase) {
        this.verifyPartnerEmployeeUseCase = verifyPartnerEmployeeUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partner:employee:verify:own')")
    public PartnerEmployeeVerificationDto verifyPartnerEmployee(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody PartnerEmployeeVerificationRequest request
    ) {
        return verifyPartnerEmployeeUseCase.verifyPartnerEmployee(partnerCompanyId, request);
    }
}

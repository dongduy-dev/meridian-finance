package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.port.in.QueryOwnPartnerEmployeeVerificationUseCase;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies")
public class PartnerEmployeeVerificationController {

    private final VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase;
    private final QueryOwnPartnerEmployeeVerificationUseCase queryOwnPartnerEmployeeVerificationUseCase;

    public PartnerEmployeeVerificationController(
            VerifyPartnerEmployeeUseCase verifyPartnerEmployeeUseCase,
            QueryOwnPartnerEmployeeVerificationUseCase queryOwnPartnerEmployeeVerificationUseCase
    ) {
        this.verifyPartnerEmployeeUseCase = verifyPartnerEmployeeUseCase;
        this.queryOwnPartnerEmployeeVerificationUseCase = queryOwnPartnerEmployeeVerificationUseCase;
    }

    @GetMapping("/employee-verifications")
    @PreAuthorize("hasAuthority('partner:employee:verify:own')")
    public List<OwnPartnerEmployeeVerificationDto> getCurrentOwnVerifications() {
        return queryOwnPartnerEmployeeVerificationUseCase.getCurrentOwnVerifications();
    }

    @PostMapping("/{partnerCompanyId}/employee-verifications")
    @PreAuthorize("hasAuthority('partner:employee:verify:own')")
    public PartnerEmployeeVerificationDto verifyPartnerEmployee(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody PartnerEmployeeVerificationRequest request
    ) {
        return verifyPartnerEmployeeUseCase.verifyPartnerEmployee(partnerCompanyId, request);
    }
}

package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.dto.CreatePartnerCompanyRequest;
import com.meridian.platform.partner.application.dto.UpdatePartnerCompanyRequest;
import com.meridian.platform.partner.application.dto.ChangePartnerCompanyStatusRequest;
import com.meridian.platform.partner.application.port.in.ManagePartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerCompanyUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies")
public class PartnerCompanyController {

    private final QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;
    private final ManagePartnerCompanyUseCase managePartnerCompanyUseCase;

    public PartnerCompanyController(
            QueryPartnerCompanyUseCase queryPartnerCompanyUseCase,
            ManagePartnerCompanyUseCase managePartnerCompanyUseCase
    ) {
        this.queryPartnerCompanyUseCase = queryPartnerCompanyUseCase;
        this.managePartnerCompanyUseCase = managePartnerCompanyUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner:read')")
    public List<PartnerCompanyDto> getPartnerCompanies() {
        return queryPartnerCompanyUseCase.getPartnerCompanies();
    }

    @GetMapping("/{partnerCompanyId}")
    @PreAuthorize("hasAuthority('partner:read')")
    public PartnerCompanyDto getPartnerCompanyById(
            @PathVariable UUID partnerCompanyId
    ) {
        return queryPartnerCompanyUseCase.getPartnerCompanyById(partnerCompanyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('partner:manage')")
    public PartnerCompanyDto create(@Valid @RequestBody CreatePartnerCompanyRequest request) {
        return managePartnerCompanyUseCase.create(request);
    }

    @PutMapping("/{partnerCompanyId}")
    @PreAuthorize("hasAuthority('partner:manage')")
    public PartnerCompanyDto update(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody UpdatePartnerCompanyRequest request
    ) {
        return managePartnerCompanyUseCase.update(partnerCompanyId, request);
    }

    @PostMapping("/{partnerCompanyId}/status")
    @PreAuthorize("hasAuthority('partner:manage')")
    public PartnerCompanyDto changeStatus(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody ChangePartnerCompanyStatusRequest request
    ) {
        return managePartnerCompanyUseCase.changeStatus(partnerCompanyId, request);
    }
}

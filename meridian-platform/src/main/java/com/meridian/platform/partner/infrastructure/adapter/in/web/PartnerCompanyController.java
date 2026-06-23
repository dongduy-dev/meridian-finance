package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.domain.port.in.QueryPartnerCompanyUseCase;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies")
public class PartnerCompanyController {

    private final QueryPartnerCompanyUseCase queryPartnerCompanyUseCase;

    public PartnerCompanyController(QueryPartnerCompanyUseCase queryPartnerCompanyUseCase) {
        this.queryPartnerCompanyUseCase = queryPartnerCompanyUseCase;
    }

    @GetMapping
    public List<PartnerCompanyDto> getPartnerCompanies() {
        return queryPartnerCompanyUseCase.getPartnerCompanies();
    }

    @GetMapping("/{partnerCompanyId}")
    public PartnerCompanyDto getPartnerCompanyById(
            @PathVariable UUID partnerCompanyId
    ) {
        return queryPartnerCompanyUseCase.getPartnerCompanyById(partnerCompanyId);
    }
}
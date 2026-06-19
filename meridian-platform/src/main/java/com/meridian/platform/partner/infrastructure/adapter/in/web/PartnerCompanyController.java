package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.domain.port.in.QueryPartnerCompanyUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
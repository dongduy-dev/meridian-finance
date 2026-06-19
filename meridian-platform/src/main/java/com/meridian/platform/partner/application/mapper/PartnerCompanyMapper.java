package com.meridian.platform.partner.application.mapper;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import org.springframework.stereotype.Component;

@Component
public class PartnerCompanyMapper {

    public PartnerCompanyDto toDto(PartnerCompany partnerCompany) {
        return new PartnerCompanyDto(
                partnerCompany.id(),
                partnerCompany.companyCode(),
                partnerCompany.name(),
                partnerCompany.status().name(),
                partnerCompany.salaryAdvancePolicyLimit()
        );
    }
}
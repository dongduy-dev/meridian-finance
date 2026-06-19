package com.meridian.platform.partner.domain.port.in;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;

import java.util.List;

public interface QueryPartnerCompanyUseCase {
    List<PartnerCompanyDto> getPartnerCompanies();
}
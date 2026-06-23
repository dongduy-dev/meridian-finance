package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;

import java.util.List;
import java.util.UUID;

public interface QueryPartnerCompanyUseCase {

    List<PartnerCompanyDto> getPartnerCompanies();

    PartnerCompanyDto getPartnerCompanyById(UUID partnerCompanyId);
}

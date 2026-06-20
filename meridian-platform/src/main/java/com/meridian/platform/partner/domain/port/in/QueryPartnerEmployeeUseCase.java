package com.meridian.platform.partner.domain.port.in;

import com.meridian.platform.partner.domain.model.PartnerEmployee;

import java.util.List;
import java.util.UUID;

public interface QueryPartnerEmployeeUseCase {

    List<PartnerEmployee> getPartnerEmployeesByCompanyId(UUID partnerCompanyId);
}
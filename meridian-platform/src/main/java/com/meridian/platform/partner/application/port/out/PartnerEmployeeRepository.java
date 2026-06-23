package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerEmployee;

import java.util.List;
import java.util.UUID;

public interface PartnerEmployeeRepository {
    List<PartnerEmployee> findByPartnerCompanyId(UUID partnerCompanyId);
    List<PartnerEmployee> findActiveByPartnerCompanyId(UUID companyId);
}

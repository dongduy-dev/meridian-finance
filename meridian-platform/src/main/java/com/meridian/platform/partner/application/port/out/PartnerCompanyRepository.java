package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerCompany;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerCompanyRepository {
    List<PartnerCompany> findAll();
    Optional<PartnerCompany> findById(UUID partnerCompanyId);
}

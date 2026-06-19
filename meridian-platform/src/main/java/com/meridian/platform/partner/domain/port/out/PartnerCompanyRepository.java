package com.meridian.platform.partner.domain.port.out;

import com.meridian.platform.partner.domain.model.PartnerCompany;

import java.util.List;

public interface PartnerCompanyRepository {
    List<PartnerCompany> findAll();
}
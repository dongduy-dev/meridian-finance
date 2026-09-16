package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerCompany;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerCompanyRepository {
    List<PartnerCompany> findAll();
    Optional<PartnerCompany> findById(UUID partnerCompanyId);
    default Optional<PartnerCompany> findByIdForUpdate(UUID partnerCompanyId) {
        return findById(partnerCompanyId);
    }
    default boolean existsByCompanyCode(String companyCode) {
        return findAll().stream().anyMatch(company -> company.companyCode().equals(companyCode));
    }
    default PartnerCompany save(PartnerCompany partnerCompany) {
        throw new UnsupportedOperationException("Partner Company save is not implemented.");
    }
}

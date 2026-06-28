package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerEmployee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerEmployeeRepository {
    Optional<PartnerEmployee> findById(UUID partnerEmployeeId);
    List<PartnerEmployee> findByPartnerCompanyId(UUID partnerCompanyId);
    List<PartnerEmployee> findActiveByPartnerCompanyId(UUID companyId);
    List<PartnerEmployee> findByVerificationEvidence(
            UUID partnerCompanyId,
            UUID importBatchId,
            String identityReference,
            String employeeCode
    );
}
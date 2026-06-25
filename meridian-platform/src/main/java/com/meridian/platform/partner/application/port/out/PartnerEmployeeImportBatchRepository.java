package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerEmployeeImportBatchRepository {

    List<PartnerEmployeeImportBatch> findByPartnerCompanyId(UUID partnerCompanyId);

    Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyId(UUID partnerCompanyId);
}
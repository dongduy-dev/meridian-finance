package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerEmployeeImportBatchRepository {

    List<PartnerEmployeeImportBatch> findByPartnerCompanyId(UUID partnerCompanyId);

    Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyId(UUID partnerCompanyId);

    Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
            UUID partnerCompanyId,
            String effectiveMonth
    );

    default void acquireRequestLock(UUID requestId) {
        throw new UnsupportedOperationException("Partner import request locking is not implemented.");
    }

    default Optional<PartnerEmployeeImportBatch> findByRequestId(UUID requestId) {
        return Optional.empty();
    }

    default PartnerEmployeeImportBatch save(PartnerEmployeeImportBatch importBatch) {
        throw new UnsupportedOperationException("Partner import batch save is not implemented.");
    }
}

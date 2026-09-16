package com.meridian.platform.partner.domain.model;

import java.util.UUID;
import java.util.List;

public record PartnerEmployeeImportBatch(
        UUID id,
        UUID partnerCompanyId,
        String effectiveMonth,
        PartnerEmployeeImportBatchStatus status,
        int validRowCount,
        int invalidRowCount,
        UUID requestId,
        String requestFingerprint,
        List<PartnerEmployeeImportRejection> rejections
) {
    public PartnerEmployeeImportBatch {
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
    }

    public PartnerEmployeeImportBatch(
            UUID id,
            UUID partnerCompanyId,
            String effectiveMonth,
            PartnerEmployeeImportBatchStatus status,
            int validRowCount,
            int invalidRowCount
    ) {
        this(id, partnerCompanyId, effectiveMonth, status, validRowCount, invalidRowCount,
                null, null, List.of());
    }
}

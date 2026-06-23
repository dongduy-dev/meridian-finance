package com.meridian.platform.partner.domain.model;

import java.util.UUID;

public record PartnerEmployeeImportBatch(
        UUID id,
        UUID partnerCompanyId,
        String effectiveMonth,
        PartnerEmployeeImportBatchStatus status,
        int validRowCount,
        int invalidRowCount
) {
}
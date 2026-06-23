package com.meridian.platform.partner.application.dto;

import java.util.UUID;

public record PartnerEmployeeImportBatchDto(
        UUID id,
        UUID partnerCompanyId,
        String effectiveMonth,
        String status,
        int validRowCount,
        int invalidRowCount
) {
}
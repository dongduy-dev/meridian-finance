package com.meridian.platform.partner.application.dto;

import java.util.List;
import java.util.UUID;

public record PartnerEmployeeImportResultDto(
        UUID importBatchId,
        UUID partnerCompanyId,
        String effectiveMonth,
        String status,
        int validRowCount,
        int invalidRowCount,
        List<RowRejection> rejections
) {
    public PartnerEmployeeImportResultDto {
        rejections = List.copyOf(rejections);
    }

    public record RowRejection(int rowIndex, String errorCode, String reason) {
    }
}

package com.meridian.platform.partner.application.dto;

import java.util.List;
import java.util.UUID;

public record PartnerEmployeeImportBatchDetailDto(
        UUID id,
        UUID partnerCompanyId,
        String effectiveMonth,
        String status,
        int validRowCount,
        int invalidRowCount,
        List<PartnerEmployeeDto> employees,
        List<PartnerEmployeeImportResultDto.RowRejection> rejections
) {
    public PartnerEmployeeImportBatchDetailDto {
        employees = List.copyOf(employees);
        rejections = List.copyOf(rejections);
    }
}

package com.meridian.platform.partner.application.dto;

import java.util.List;
import java.util.UUID;

public record CurrentPartnerEmployeeSnapshotDto(
        UUID partnerCompanyId,
        String effectiveMonth,
        UUID authoritativeBatchId,
        List<PartnerEmployeeDto> employees
) {
}

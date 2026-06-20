package com.meridian.platform.partner.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerEmployeeDto(
        UUID id,
        UUID partnerCompanyId,
        UUID importBatchId,
        String employeeCode,
        String identityReference,
        BigDecimal salaryAmount,
        BigDecimal salaryAdvanceLimit,
        String employmentStatus,
        boolean active
) {
}
package com.meridian.platform.partner.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerEmployee(
        UUID id,
        UUID partnerCompanyId,
        UUID importBatchId,
        String employeeCode,
        String identityReference,
        BigDecimal salaryAmount,
        BigDecimal salaryAdvanceLimit,
        PartnerEmployeeStatus employmentStatus,
        boolean active
) {
}
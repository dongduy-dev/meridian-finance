package com.meridian.platform.partner.application.dto;

import java.math.BigDecimal;

public record PartnerEmployeeImportRowRequest(
        String employeeCode,
        String identityReference,
        BigDecimal salaryAmount,
        BigDecimal salaryAdvanceLimit,
        String employmentStatus,
        Boolean active
) {
}

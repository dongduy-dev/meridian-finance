package com.meridian.platform.partner.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerPartnerEmployeeLinkSnapshotDto(
        UUID customerId,
        UUID customerPartnerEmployeeLinkId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID sourceImportBatchId,
        String employeeVerificationOutcome,
        BigDecimal partnerCompanySalaryAdvanceLimit,
        BigDecimal employeeSalaryAmount,
        BigDecimal employeeSalaryAdvanceLimit,
        LocalDateTime lastVerifiedAt,
        LocalDateTime lastRefreshedAt
) {
}

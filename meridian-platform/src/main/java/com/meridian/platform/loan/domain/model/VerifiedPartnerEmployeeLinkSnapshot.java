package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VerifiedPartnerEmployeeLinkSnapshot(
        UUID customerId,
        UUID customerPartnerEmployeeLinkId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID sourceImportBatchId,
        BigDecimal employeeSalaryAdvanceLimit,
        LocalDateTime lastVerifiedAt,
        LocalDateTime lastRefreshedAt
) {
}

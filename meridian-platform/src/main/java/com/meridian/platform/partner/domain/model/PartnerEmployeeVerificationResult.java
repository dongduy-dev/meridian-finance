package com.meridian.platform.partner.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerEmployeeVerificationResult(
        UUID customerId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID customerPartnerEmployeeLinkId,
        EmployeeVerificationOutcome outcome,
        CustomerPartnerEmployeeLinkStatus linkStatus,
        boolean manualReviewRequired,
        BigDecimal salaryAmount,
        BigDecimal salaryAdvanceLimit
) {
}

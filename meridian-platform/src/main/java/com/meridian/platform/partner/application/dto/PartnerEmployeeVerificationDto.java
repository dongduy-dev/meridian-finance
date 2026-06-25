package com.meridian.platform.partner.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerEmployeeVerificationDto(
        UUID customerId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID customerPartnerEmployeeLinkId,
        String outcome,
        String linkStatus,
        boolean manualReviewRequired,
        BigDecimal salaryAmount,
        BigDecimal salaryAdvanceLimit
) {
}

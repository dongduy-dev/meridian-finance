package com.meridian.platform.partner.application.dto;

import java.util.UUID;

public record PartnerEmployeeVerificationDto(
        UUID customerId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID customerPartnerEmployeeLinkId,
        String outcome,
        String linkStatus,
        boolean manualReviewRequired
) {
}

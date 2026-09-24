package com.meridian.platform.partner.application.dto;

import java.util.UUID;

public record OwnPartnerEmployeeVerificationDto(
        UUID partnerCompanyId,
        String outcome,
        boolean manualReviewRequired
) {
}

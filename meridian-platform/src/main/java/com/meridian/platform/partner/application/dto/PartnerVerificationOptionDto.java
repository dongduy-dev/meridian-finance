package com.meridian.platform.partner.application.dto;

import java.util.UUID;

public record PartnerVerificationOptionDto(
        UUID partnerCompanyId,
        String companyCode,
        String name
) {
}

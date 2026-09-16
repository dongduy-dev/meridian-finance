package com.meridian.platform.partner.application.dto;

import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import jakarta.validation.constraints.NotNull;

public record ChangePartnerCompanyStatusRequest(@NotNull PartnerCompanyStatus status) {
}

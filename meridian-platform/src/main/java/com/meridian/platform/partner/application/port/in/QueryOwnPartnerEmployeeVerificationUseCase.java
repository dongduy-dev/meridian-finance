package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;

import java.util.UUID;

public interface QueryOwnPartnerEmployeeVerificationUseCase {

    OwnPartnerEmployeeVerificationDto getLatestOwnVerification(UUID partnerCompanyId);
}

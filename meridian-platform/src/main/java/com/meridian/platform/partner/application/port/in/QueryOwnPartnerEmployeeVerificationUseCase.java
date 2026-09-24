package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;

import java.util.List;

public interface QueryOwnPartnerEmployeeVerificationUseCase {

    List<OwnPartnerEmployeeVerificationDto> getCurrentOwnVerifications();
}

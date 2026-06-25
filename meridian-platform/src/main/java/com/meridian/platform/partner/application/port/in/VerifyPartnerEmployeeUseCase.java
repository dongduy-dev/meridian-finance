package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;

import java.util.UUID;

public interface VerifyPartnerEmployeeUseCase {

    PartnerEmployeeVerificationDto verifyPartnerEmployee(
            UUID partnerCompanyId,
            PartnerEmployeeVerificationRequest request
    );
}

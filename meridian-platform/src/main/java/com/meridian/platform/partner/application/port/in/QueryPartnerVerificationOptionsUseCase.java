package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerVerificationOptionDto;

import java.util.List;

public interface QueryPartnerVerificationOptionsUseCase {

    List<PartnerVerificationOptionDto> query();
}

package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.ChangePartnerCompanyStatusRequest;
import com.meridian.platform.partner.application.dto.CreatePartnerCompanyRequest;
import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.dto.UpdatePartnerCompanyRequest;

import java.util.UUID;

public interface ManagePartnerCompanyUseCase {
    PartnerCompanyDto create(CreatePartnerCompanyRequest request);
    PartnerCompanyDto update(UUID partnerCompanyId, UpdatePartnerCompanyRequest request);
    PartnerCompanyDto changeStatus(UUID partnerCompanyId, ChangePartnerCompanyStatusRequest request);
}

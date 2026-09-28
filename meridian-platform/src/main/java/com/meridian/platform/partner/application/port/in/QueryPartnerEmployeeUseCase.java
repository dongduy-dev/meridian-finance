package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.dto.CurrentPartnerEmployeeSnapshotDto;

import java.util.List;
import java.util.UUID;

public interface QueryPartnerEmployeeUseCase {

    List<PartnerEmployeeDto> getPartnerEmployeesByCompanyId(UUID partnerCompanyId, boolean activeOnly);

    CurrentPartnerEmployeeSnapshotDto getCurrentPartnerEmployeeSnapshot(UUID partnerCompanyId);
}

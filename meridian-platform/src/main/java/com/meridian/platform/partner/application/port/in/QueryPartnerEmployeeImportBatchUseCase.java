package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDto;

import java.util.List;
import java.util.UUID;

public interface QueryPartnerEmployeeImportBatchUseCase {

    List<PartnerEmployeeImportBatchDto> getImportBatchesByPartnerCompanyId(UUID partnerCompanyId);
}

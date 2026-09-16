package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;

import java.util.UUID;

public interface ImportPartnerEmployeesUseCase {
    PartnerEmployeeImportResultDto importEmployees(UUID partnerCompanyId, ImportPartnerEmployeesRequest request);
}

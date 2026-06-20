package com.meridian.platform.partner.application.mapper;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.domain.model.PartnerEmployee;

public final class PartnerEmployeeMapper {

    private PartnerEmployeeMapper() {
    }

    public static PartnerEmployeeDto toDto(PartnerEmployee partnerEmployee) {
        return new PartnerEmployeeDto(
                partnerEmployee.id(),
                partnerEmployee.partnerCompanyId(),
                partnerEmployee.importBatchId(),
                partnerEmployee.employeeCode(),
                partnerEmployee.identityReference(),
                partnerEmployee.salaryAmount(),
                partnerEmployee.salaryAdvanceLimit(),
                partnerEmployee.employmentStatus().name(),
                partnerEmployee.active()
        );
    }
}
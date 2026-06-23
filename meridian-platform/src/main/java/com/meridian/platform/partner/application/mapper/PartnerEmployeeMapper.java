package com.meridian.platform.partner.application.mapper;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import org.springframework.stereotype.Component;

@Component
public class PartnerEmployeeMapper {

    public PartnerEmployeeDto toDto(PartnerEmployee partnerEmployee) {
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

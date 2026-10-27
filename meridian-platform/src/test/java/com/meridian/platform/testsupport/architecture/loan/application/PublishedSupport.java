package com.meridian.platform.testsupport.architecture.loan.application;

import com.meridian.platform.customer.application.port.in.SensitiveContractBankAccount;
import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto;
import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto;

public record PublishedSupport(
        CustomerPartnerEmployeeEligibilityDto eligibility,
        CustomerPartnerEmployeeEligibilityDto.Status status,
        CustomerPartnerEmployeeLinkSnapshotDto snapshot,
        SensitiveContractBankAccount bankAccount
) {
}

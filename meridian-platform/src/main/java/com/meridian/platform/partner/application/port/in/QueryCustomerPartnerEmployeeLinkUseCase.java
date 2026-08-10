package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto;
import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto;

import java.util.Optional;
import java.util.UUID;

public interface QueryCustomerPartnerEmployeeLinkUseCase {

    CustomerPartnerEmployeeEligibilityDto inspectEligibility(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );

    CustomerPartnerEmployeeEligibilityDto inspectCurrentEligibility(UUID customerId);

    default Optional<CustomerPartnerEmployeeLinkSnapshotDto> findVerifiedActiveLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return inspectEligibility(customerId, customerPartnerEmployeeLinkId).optionalSnapshot();
    }
}

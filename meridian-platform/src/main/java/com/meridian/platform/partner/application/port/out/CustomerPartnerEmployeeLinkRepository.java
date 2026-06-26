package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPartnerEmployeeLinkRepository {

    Optional<CustomerPartnerEmployeeLink> findById(UUID customerPartnerEmployeeLinkId);

    Optional<CustomerPartnerEmployeeLink> findCurrentByCustomerIdAndPartnerCompanyId(
            UUID customerId,
            UUID partnerCompanyId
    );

    CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink);
}
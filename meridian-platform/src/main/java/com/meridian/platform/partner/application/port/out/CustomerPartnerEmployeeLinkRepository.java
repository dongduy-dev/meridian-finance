package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPartnerEmployeeLinkRepository {

    Optional<CustomerPartnerEmployeeLink> findById(UUID customerPartnerEmployeeLinkId);

    Optional<CustomerPartnerEmployeeLink> findCurrentByCustomerIdAndPartnerCompanyId(
            UUID customerId,
            UUID partnerCompanyId
    );

    List<CustomerPartnerEmployeeLink> findByCustomerId(UUID customerId);

    CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink);
}

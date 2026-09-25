package com.meridian.platform.partner.application.port.out;

import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPartnerEmployeeLinkRepository {

    Optional<CustomerPartnerEmployeeLink> findById(UUID customerPartnerEmployeeLinkId);

    Optional<CustomerPartnerEmployeeLink> findCurrentVerifiedByCustomerId(UUID customerId);

    Optional<CustomerPartnerEmployeeLink> findCurrentVerifiedByCustomerIdForUpdate(UUID customerId);

    List<UUID> findVerifiedLinkIdsByPartnerCompanyId(UUID partnerCompanyId);

    Optional<CustomerPartnerEmployeeLink> findVerifiedByIdAndPartnerCompanyIdForUpdate(
            UUID customerPartnerEmployeeLinkId,
            UUID partnerCompanyId
    );

    void acquireCustomerEmploymentLock(UUID customerId);

    CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink);

    CustomerPartnerEmployeeLink saveAndFlush(CustomerPartnerEmployeeLink customerPartnerEmployeeLink);
}

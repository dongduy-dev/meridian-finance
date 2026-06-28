package com.meridian.platform.partner.application.port.in;

import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto;

import java.util.Optional;
import java.util.UUID;

public interface QueryCustomerPartnerEmployeeLinkUseCase {

    Optional<CustomerPartnerEmployeeLinkSnapshotDto> findVerifiedActiveLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );
}

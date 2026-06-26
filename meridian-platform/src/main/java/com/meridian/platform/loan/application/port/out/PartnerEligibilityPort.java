package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface PartnerEligibilityPort {

    Optional<VerifiedPartnerEmployeeLinkSnapshot> findVerifiedEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );
}

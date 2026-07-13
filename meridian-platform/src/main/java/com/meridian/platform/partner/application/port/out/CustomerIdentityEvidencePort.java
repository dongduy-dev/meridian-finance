package com.meridian.platform.partner.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityEvidencePort {

    Optional<CustomerIdentityEvidenceSnapshot> findIdentityEvidenceByCustomerId(UUID customerId);
}
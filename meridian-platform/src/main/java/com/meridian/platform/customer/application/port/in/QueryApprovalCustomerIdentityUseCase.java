package com.meridian.platform.customer.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Published purpose-specific contract; callers authorize the decision workflow. */
public interface QueryApprovalCustomerIdentityUseCase {
    Optional<ApprovalCustomerIdentity> findByCustomerId(UUID customerId);
}

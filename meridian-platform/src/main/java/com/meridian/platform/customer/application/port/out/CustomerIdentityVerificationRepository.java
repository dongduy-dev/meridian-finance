package com.meridian.platform.customer.application.port.out;

import com.meridian.platform.customer.domain.model.CustomerIdentityVerification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityVerificationRepository {
    void lockDecisionRequest(UUID requestId);
    Optional<CustomerIdentityVerification> findByDecisionRequest(UUID requestId);
    Optional<CustomerIdentityVerification> findById(UUID id);
    List<CustomerIdentityVerification> findByCustomer(UUID customerId);
    List<CustomerIdentityVerification> findPending(int offset, int limit);
    CustomerIdentityVerification save(CustomerIdentityVerification verification);
}

package com.meridian.platform.customer.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Customer-owned current facts for purpose-authorized LoanAccount servicing. */
public interface QueryServicingCustomerContextUseCase {
    Optional<BusinessIdentity> findBusinessIdentityByCustomerId(UUID customerId);
    Optional<CurrentContact> findCurrentContactByCustomerId(UUID customerId);

    record BusinessIdentity(String customerNumber, String fullName) {
        @Override public String toString() { return "ServicingBusinessIdentity[identity=redacted]"; }
    }

    record CurrentContact(String customerNumber, String fullName, String phoneNumber) {
        @Override public String toString() { return "ServicingCurrentContact[contact=redacted]"; }
    }
}

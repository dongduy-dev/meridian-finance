package com.meridian.platform.loan.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface ServicingCustomerContextPort {
    Optional<BusinessIdentity> findBusinessIdentityByCustomerId(UUID customerId);
    Optional<CurrentContact> findCurrentContactByCustomerId(UUID customerId);

    record BusinessIdentity(String customerNumber, String fullName) {
        @Override public String toString() { return "ServicingBusinessIdentity[identity=redacted]"; }
    }

    record CurrentContact(String customerNumber, String fullName, String phoneNumber) {
        @Override public String toString() { return "ServicingCurrentContact[contact=redacted]"; }
    }
}

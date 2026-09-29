package com.meridian.platform.identity.application.port.out;

import java.util.UUID;

public interface CustomerDigitalAccessVerificationPort {

    void requireExistingCustomer(UUID customerId);

    VerifiedCustomer verifyForActivation(UUID customerId, String identityReference);

    record VerifiedCustomer(UUID customerId, String displayName) {
    }
}

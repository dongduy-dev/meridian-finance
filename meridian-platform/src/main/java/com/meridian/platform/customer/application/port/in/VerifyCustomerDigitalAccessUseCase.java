package com.meridian.platform.customer.application.port.in;

import java.util.UUID;

public interface VerifyCustomerDigitalAccessUseCase {

    void requireExistingCustomer(UUID customerId);

    CustomerDigitalAccessCandidate verifyForActivation(UUID customerId, String identityReference);
}

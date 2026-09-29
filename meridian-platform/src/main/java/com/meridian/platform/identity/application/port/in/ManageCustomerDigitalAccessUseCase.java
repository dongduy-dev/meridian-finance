package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;
import com.meridian.platform.identity.application.dto.EnableCustomerDigitalAccessRequest;

import java.util.UUID;

public interface ManageCustomerDigitalAccessUseCase {
    CustomerDigitalAccessDto status(UUID customerId);

    CustomerDigitalAccessDto enable(UUID customerId, EnableCustomerDigitalAccessRequest request);
}

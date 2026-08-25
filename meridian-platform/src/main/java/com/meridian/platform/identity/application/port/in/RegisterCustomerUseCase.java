package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import com.meridian.platform.identity.application.dto.CustomerRegistrationResponse;

public interface RegisterCustomerUseCase {

    CustomerRegistrationResponse register(CustomerRegistrationRequest request);
}

package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;

public interface UpdateOwnCustomerProfileUseCase {

    CustomerDto updateOwnProfile(UpdateCustomerProfileRequest request);
}

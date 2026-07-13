package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.CustomerDto;

public interface QueryOwnCustomerUseCase {

    CustomerDto getOwnCustomer();
}

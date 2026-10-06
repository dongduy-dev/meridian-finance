package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.CorrectIdentityReferenceRequest;
import com.meridian.platform.customer.application.dto.CustomerDto;
import java.util.UUID;

public interface CorrectCustomerIdentityReferenceUseCase {
    CustomerDto correctOwn(CorrectIdentityReferenceRequest request);
    CustomerDto correctForIntake(UUID customerId, CorrectIdentityReferenceRequest request);
}

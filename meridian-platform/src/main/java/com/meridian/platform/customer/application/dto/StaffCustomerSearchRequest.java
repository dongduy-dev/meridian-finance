package com.meridian.platform.customer.application.dto;

import jakarta.validation.constraints.Size;

public record StaffCustomerSearchRequest(
        @Size(max = 50) String customerNumber,
        @Size(max = 100) String identityReference
) {
}

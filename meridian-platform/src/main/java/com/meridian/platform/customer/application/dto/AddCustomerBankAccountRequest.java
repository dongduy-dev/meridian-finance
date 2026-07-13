package com.meridian.platform.customer.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCustomerBankAccountRequest(
        @NotBlank @Size(max = 50) String bankCode,
        @NotBlank @Size(max = 150) String bankNameSnapshot,
        @NotBlank @Size(max = 200) String accountHolderName,
        @NotBlank @Size(max = 100) String accountNumber
) {
}

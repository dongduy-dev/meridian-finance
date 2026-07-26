package com.meridian.platform.customer.application.port.out;

import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;

public interface CustomerSensitiveValueProtector {

    ProtectedSensitiveValue protectIdentityReference(String identityReference);

    ProtectedSensitiveValue protectBankAccountNumber(String bankCode, String accountNumber);

    default byte[] revealToBytes(ProtectedSensitiveValue protectedValue) {
        throw new UnsupportedOperationException("Mutable sensitive-value reveal is not supported.");
    }

    String reveal(ProtectedSensitiveValue protectedValue);
}

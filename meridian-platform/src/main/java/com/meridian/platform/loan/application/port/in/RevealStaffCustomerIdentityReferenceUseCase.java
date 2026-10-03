package com.meridian.platform.loan.application.port.in;

import java.util.UUID;

public interface RevealStaffCustomerIdentityReferenceUseCase {
    Result reveal(UUID loanApplicationId);

    record Result(UUID loanApplicationId, String identityReference) {
        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId + ", identityReference=redacted]";
        }
    }
}

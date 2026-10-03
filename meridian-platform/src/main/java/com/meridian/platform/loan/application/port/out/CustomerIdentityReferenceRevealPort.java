package com.meridian.platform.loan.application.port.out;

import java.util.UUID;

public interface CustomerIdentityReferenceRevealPort {
    Result reveal(UUID customerId, UUID loanApplicationId);

    record Result(String identityReference) {
        @Override
        public String toString() {
            return "Result[identityReference=redacted]";
        }
    }
}

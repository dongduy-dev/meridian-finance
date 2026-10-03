package com.meridian.platform.customer.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Internal case-purpose contract. Customer identifiers are resolved by Loan, never by a browser. */
public interface RevealCustomerIdentityReferenceUseCase {
    Result reveal(Command command);

    record Command(UUID customerId, UUID loanApplicationId) {
        public Command {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        }
    }

    record Result(String identityReference) {
        @Override
        public String toString() {
            return "Result[identityReference=redacted]";
        }
    }
}

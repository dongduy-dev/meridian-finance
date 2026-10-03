package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record StaffLoanAccountCustomerContextDto(
        UUID loanApplicationId, UUID loanAccountId, CustomerContext customer
) {
    public record CustomerContext(String customerNumber, String fullName, String phoneNumber) {
        @Override public String toString() { return "ServicingCustomerContext[contact=redacted]"; }
    }
}

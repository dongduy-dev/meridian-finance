package com.meridian.platform.loan.application.port.out;

public record AccountingCustomerIdentitySnapshot(String customerNumber, String fullName) {
    @Override
    public String toString() {
        return "AccountingCustomerIdentitySnapshot[identity=redacted]";
    }
}

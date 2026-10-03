package com.meridian.platform.loan.application.port.out;

public record AccountingCustomerIdentitySnapshot(String customerNumber, String fullName, String phoneNumber) {
    @Override
    public String toString() {
        return "AccountingCustomerIdentitySnapshot[identity=redacted]";
    }
}

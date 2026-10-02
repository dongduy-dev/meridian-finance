package com.meridian.platform.customer.application.port.in;

public record AccountingCustomerIdentity(String customerNumber, String fullName, String phoneNumber) {
    @Override
    public String toString() {
        return "AccountingCustomerIdentity[identity=redacted]";
    }
}

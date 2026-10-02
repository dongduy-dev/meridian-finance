package com.meridian.platform.customer.application.port.in;

/** Customer-owned business identity for independent lending decisions. */
public record ApprovalCustomerIdentity(String customerNumber, String fullName) {
    @Override
    public String toString() {
        return "ApprovalCustomerIdentity[identity=redacted]";
    }
}

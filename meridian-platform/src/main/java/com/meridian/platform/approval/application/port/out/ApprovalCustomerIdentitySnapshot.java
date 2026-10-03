package com.meridian.platform.approval.application.port.out;

public record ApprovalCustomerIdentitySnapshot(String customerNumber, String fullName) {
    @Override
    public String toString() {
        return "ApprovalCustomerIdentitySnapshot[identity=redacted]";
    }
}

package com.meridian.platform.customer.application.port.in;

public record CustomerLoanCaseContact(
        String customerNumber,
        String fullName,
        String phoneNumber,
        String maskedIdentityReference
) {
}

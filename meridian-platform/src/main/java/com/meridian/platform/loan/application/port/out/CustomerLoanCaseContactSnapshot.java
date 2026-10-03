package com.meridian.platform.loan.application.port.out;

public record CustomerLoanCaseContactSnapshot(
        String customerNumber,
        String fullName,
        String phoneNumber,
        String maskedIdentityReference
) {
}

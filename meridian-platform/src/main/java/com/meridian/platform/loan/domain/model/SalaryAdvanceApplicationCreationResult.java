package com.meridian.platform.loan.domain.model;

public record SalaryAdvanceApplicationCreationResult(
        LoanApplication loanApplication,
        SalaryAdvanceLimit salaryAdvanceLimit,
        SalaryAdvanceVerification salaryAdvanceVerification
) {
}

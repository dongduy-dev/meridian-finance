package com.meridian.platform.loan.domain.model.salaryadvance;

import com.meridian.platform.loan.domain.model.LoanApplication;

public record SalaryAdvanceApplicationCreationResult(
        LoanApplication loanApplication,
        SalaryAdvanceLimit salaryAdvanceLimit,
        SalaryAdvanceVerification salaryAdvanceVerification
) {
}

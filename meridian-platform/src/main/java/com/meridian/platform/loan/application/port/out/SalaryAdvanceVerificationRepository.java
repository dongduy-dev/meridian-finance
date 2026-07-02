package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;

import java.util.Optional;
import java.util.UUID;

public interface SalaryAdvanceVerificationRepository {

    SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification);

    Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId);
}

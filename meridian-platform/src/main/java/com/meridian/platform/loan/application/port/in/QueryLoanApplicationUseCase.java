package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;

import java.util.UUID;

public interface QueryLoanApplicationUseCase {

    LoanApplicationStatusDto query(UUID loanApplicationId);
}

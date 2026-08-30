package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.dto.CustomerLoanApplicationSummaryDto;

import java.util.List;
import java.util.UUID;

public interface QueryLoanApplicationUseCase {

    LoanApplicationStatusDto query(UUID loanApplicationId);

    default List<CustomerLoanApplicationSummaryDto> queryOwnApplications() {
        throw new UnsupportedOperationException("Customer application index is not implemented.");
    }
}

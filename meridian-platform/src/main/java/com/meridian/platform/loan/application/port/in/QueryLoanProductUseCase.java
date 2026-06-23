package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.LoanProductDto;

import java.util.List;

public interface QueryLoanProductUseCase {

    List<LoanProductDto> findActiveLoanProducts();
}

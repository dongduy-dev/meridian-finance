package com.meridian.platform.loan.domain.port.in;

import com.meridian.platform.loan.domain.model.LoanProduct;

import java.util.List;

public interface QueryLoanProductUseCase {

    List<LoanProduct> findActiveLoanProducts();
}
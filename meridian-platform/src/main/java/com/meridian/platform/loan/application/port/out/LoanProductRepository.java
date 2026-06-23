package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanProduct;

import java.util.List;

public interface LoanProductRepository {

    List<LoanProduct> findAllActive();
}

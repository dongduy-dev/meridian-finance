package com.meridian.platform.loan.domain.port.out;

import com.meridian.platform.loan.domain.model.LoanProduct;

import java.util.List;

public interface LoanProductRepository {

    List<LoanProduct> findAllActive();
}
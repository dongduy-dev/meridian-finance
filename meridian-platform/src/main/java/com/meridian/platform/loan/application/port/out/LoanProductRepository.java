package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.List;
import java.util.Optional;

public interface LoanProductRepository {

    List<LoanProduct> findAllActive();

    Optional<LoanProduct> findByProductCode(ProductCode productCode);
}
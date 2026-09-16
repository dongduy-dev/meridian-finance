package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.List;
import java.util.Optional;

public interface LoanProductRepository {

    List<LoanProduct> findAllActive();

    default List<LoanProduct> findAll() {
        return findAllActive();
    }

    Optional<LoanProduct> findByProductCode(ProductCode productCode);

    default Optional<LoanProduct> findByProductCodeForUpdate(ProductCode productCode) {
        return findByProductCode(productCode);
    }

    default LoanProduct save(LoanProduct loanProduct) {
        throw new UnsupportedOperationException("Loan Product save is not implemented.");
    }
}

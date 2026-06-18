package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.domain.model.LoanProduct;

public final class LoanMapper {

    private LoanMapper() {
    }

    public static LoanProductDto toLoanProductDto(LoanProduct loanProduct) {
        return new LoanProductDto(
                loanProduct.productCode().name(),
                loanProduct.productType().name(),
                loanProduct.name(),
                loanProduct.description(),
                loanProduct.active(),
                loanProduct.minAmount(),
                loanProduct.maxAmount()
        );
    }
}
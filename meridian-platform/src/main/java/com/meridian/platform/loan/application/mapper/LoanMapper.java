package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.domain.model.LoanProduct;
import org.springframework.stereotype.Component;

@Component
public class LoanMapper {

    public LoanProductDto toLoanProductDto(LoanProduct loanProduct) {
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

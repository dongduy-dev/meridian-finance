package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.domain.model.LoanProduct;
import org.springframework.stereotype.Component;

@Component
public class AdminLoanProductMapper {
    public AdminLoanProductDto toDto(LoanProduct product) {
        return new AdminLoanProductDto(
                product.productCode().name(),
                product.productType().name(),
                product.name(),
                product.description(),
                product.active(),
                product.minAmount(),
                product.maxAmount()
        );
    }
}

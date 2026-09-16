package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.mapper.AdminLoanProductMapper;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryAdminLoanProductsServiceTest {
    @Test
    void returnsActiveAndInactiveProductsInRepositoryOrderWithoutPolicyDependencies() {
        LoanProductRepository products = mock(LoanProductRepository.class);
        when(products.findAll()).thenReturn(List.of(
                product(ProductCode.COLLATERAL_LOAN, ProductType.SECURED, false),
                product(ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED, true)
        ));

        var result = new QueryAdminLoanProductsService(products, new AdminLoanProductMapper()).findAll();

        assertEquals(List.of("COLLATERAL_LOAN", "SALARY_ADVANCE"),
                result.stream().map(item -> item.productCode()).toList());
        assertEquals(List.of(false, true), result.stream().map(item -> item.active()).toList());
    }

    private static LoanProduct product(ProductCode code, ProductType type, boolean active) {
        return new LoanProduct(
                UUID.randomUUID(), code, type, code.name(), "Description", active,
                BigDecimal.ZERO.setScale(2), BigDecimal.TEN.setScale(2)
        );
    }
}

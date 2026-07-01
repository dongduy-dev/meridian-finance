package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.List;

@Service
public class QueryLoanProductService implements QueryLoanProductUseCase {

    private final LoanProductRepository loanProductRepository;
    private final LoanMapper loanMapper;

    public QueryLoanProductService(
            LoanProductRepository loanProductRepository,
            LoanMapper loanMapper
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanMapper = loanMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanProductDto> findActiveLoanProducts() {
        return loanProductRepository.findAllActive()
                .stream()
                .map(loanMapper::toLoanProductDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoanProductDto findByProductCode(String productCode) {
        ProductCode parsedProductCode = parsedProductCode(productCode);

        return loanProductRepository.findByProductCode(parsedProductCode)
                .filter(LoanProduct::active)
                .map(loanMapper::toLoanProductDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Product was not found."
                ));
    }

    private ProductCode parsedProductCode(String productCode) {
        try{
            return ProductCode.valueOf(productCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e){
            throw new EntityNotFoundException(
                    "PRODUCT_CODE_NOT_FOUND",
                    "Product code was not found."
            );
        }
    }
}

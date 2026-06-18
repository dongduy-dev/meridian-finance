package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.domain.port.out.LoanProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryLoanProductService implements QueryLoanProductUseCase {

    private final LoanProductRepository loanProductRepository;

    public QueryLoanProductService(LoanProductRepository loanProductRepository) {
        this.loanProductRepository = loanProductRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanProduct> findActiveLoanProducts() {
        return loanProductRepository.findAllActive();
    }
}
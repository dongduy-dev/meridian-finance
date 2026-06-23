package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

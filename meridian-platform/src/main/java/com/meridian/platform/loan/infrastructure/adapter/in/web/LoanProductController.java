package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.domain.port.in.QueryLoanProductUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loan-products")
public class LoanProductController {

    private final QueryLoanProductUseCase queryLoanProductUseCase;

    public LoanProductController(QueryLoanProductUseCase queryLoanProductUseCase) {
        this.queryLoanProductUseCase = queryLoanProductUseCase;
    }

    @GetMapping
    public List<LoanProductDto> getLoanProducts() {
        return queryLoanProductUseCase.findActiveLoanProducts()
                .stream()
                .map(LoanMapper::toLoanProductDto)
                .toList();
    }
}
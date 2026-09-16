package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.mapper.AdminLoanProductMapper;
import com.meridian.platform.loan.application.port.in.QueryAdminLoanProductsUseCase;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryAdminLoanProductsService implements QueryAdminLoanProductsUseCase {
    private final LoanProductRepository products;
    private final AdminLoanProductMapper mapper;

    public QueryAdminLoanProductsService(LoanProductRepository products, AdminLoanProductMapper mapper) {
        this.products = products;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminLoanProductDto> findAll() {
        return products.findAll().stream().map(mapper::toDto).toList();
    }
}

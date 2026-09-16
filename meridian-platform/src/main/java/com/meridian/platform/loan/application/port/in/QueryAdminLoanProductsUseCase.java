package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;

import java.util.List;

public interface QueryAdminLoanProductsUseCase {
    List<AdminLoanProductDto> findAll();
}

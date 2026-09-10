package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffDisbursementCaseDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementWorkPageDto;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.UUID;

public interface QueryStaffDisbursementWorkUseCase {

    StaffDisbursementWorkPageDto queryWork(ProductCode productCode, int page, int size);

    StaffDisbursementCaseDto queryCase(UUID loanApplicationId);
}

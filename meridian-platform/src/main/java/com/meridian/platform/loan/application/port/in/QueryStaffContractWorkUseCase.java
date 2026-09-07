package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffContractCaseDto;
import com.meridian.platform.loan.application.dto.StaffContractWorkPageDto;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.UUID;

public interface QueryStaffContractWorkUseCase {

    StaffContractWorkPageDto queryWork(ProductCode productCode, int page, int size);

    StaffContractCaseDto queryCase(UUID loanApplicationId);
}

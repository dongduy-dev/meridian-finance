package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffClosureWorkPageDto;
import com.meridian.platform.loan.domain.model.ProductCode;

public interface QueryStaffClosureWorkUseCase {
    StaffClosureWorkPageDto queryWork(ProductCode productCode, int page, int size);
}

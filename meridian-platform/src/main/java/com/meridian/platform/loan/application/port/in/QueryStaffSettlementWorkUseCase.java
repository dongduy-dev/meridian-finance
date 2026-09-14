package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffSettlementWorkPageDto;
import com.meridian.platform.loan.domain.model.ProductCode;

public interface QueryStaffSettlementWorkUseCase {
    StaffSettlementWorkPageDto queryWork(ProductCode productCode, int page, int size);
}

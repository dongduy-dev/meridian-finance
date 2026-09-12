package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffServicingWorkPageDto;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;

public interface QueryStaffServicingWorkUseCase {

    StaffServicingWorkPageDto queryWork(
            ProductCode productCode,
            LoanAccountStatus accountStatus,
            int page,
            int size
    );
}

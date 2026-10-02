package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffLoanAccountCustomerContextDto;
import java.util.UUID;

public interface QueryStaffLoanAccountCustomerContextUseCase {
    StaffLoanAccountCustomerContextDto query(UUID loanApplicationId);
}

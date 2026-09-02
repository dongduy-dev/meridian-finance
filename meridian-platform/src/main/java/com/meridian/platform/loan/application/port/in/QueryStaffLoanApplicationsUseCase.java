package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.UUID;

public interface QueryStaffLoanApplicationsUseCase {

    StaffLoanApplicationPageDto queryApplications(
            ProductCode productCode,
            LoanApplicationStatus status,
            int page,
            int size
    );

    StaffLoanApplicationCaseDto queryCase(UUID loanApplicationId);
}

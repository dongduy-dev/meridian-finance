package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto;

import java.util.UUID;

public interface QueryStaffLoanApplicationVerificationUseCase {

    StaffLoanApplicationVerificationDto query(UUID loanApplicationId);
}

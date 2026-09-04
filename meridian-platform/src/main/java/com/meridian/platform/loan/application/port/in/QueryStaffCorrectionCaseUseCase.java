package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffCorrectionCaseDto;

import java.util.UUID;

public interface QueryStaffCorrectionCaseUseCase {
    StaffCorrectionCaseDto query(UUID loanApplicationId);
}

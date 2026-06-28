package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;

public interface SalaryAdvanceLimitMovementRepository {

    SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement);
}

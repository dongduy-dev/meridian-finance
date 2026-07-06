package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;

import java.util.UUID;

public interface SalaryAdvanceLimitMovementRepository {

    SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement);

    boolean existsByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    );
}

package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;

public interface QuerySalaryAdvanceReadinessUseCase {

    SalaryAdvanceReadinessDto queryReadiness();
}

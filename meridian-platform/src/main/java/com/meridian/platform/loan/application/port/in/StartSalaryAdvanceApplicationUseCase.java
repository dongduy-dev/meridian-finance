package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;

public interface StartSalaryAdvanceApplicationUseCase {

    SalaryAdvanceApplicationDto startSalaryAdvanceApplication(SalaryAdvanceApplicationRequest request);
}

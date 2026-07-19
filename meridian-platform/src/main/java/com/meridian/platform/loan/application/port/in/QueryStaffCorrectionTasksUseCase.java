package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;

import java.util.List;

public interface QueryStaffCorrectionTasksUseCase {
    List<StaffCorrectionTaskDto> findStaffTasks(LoanCorrectionTaskStatus status, int page, int size);
}

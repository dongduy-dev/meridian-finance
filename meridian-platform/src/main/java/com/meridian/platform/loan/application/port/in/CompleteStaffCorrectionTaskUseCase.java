package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;

import java.util.UUID;

public interface CompleteStaffCorrectionTaskUseCase {
    StaffCorrectionTaskDto complete(UUID taskId, CompleteCorrectionTaskRequest request);
}

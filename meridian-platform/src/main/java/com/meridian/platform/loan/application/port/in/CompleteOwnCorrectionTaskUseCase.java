package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;

import java.util.UUID;

public interface CompleteOwnCorrectionTaskUseCase {
    CustomerCorrectionTaskDto complete(UUID loanApplicationId, UUID taskId, CompleteCorrectionTaskRequest request);
}

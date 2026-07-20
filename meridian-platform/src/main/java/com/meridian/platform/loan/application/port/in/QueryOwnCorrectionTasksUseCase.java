package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;

import java.util.List;
import java.util.UUID;

public interface QueryOwnCorrectionTasksUseCase {
    List<CustomerCorrectionTaskDto> findOwnTasks(UUID loanApplicationId);
}

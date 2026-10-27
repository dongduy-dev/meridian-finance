package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CorrectionPlanRequest")
public record LoanCorrectionPlanRequest(
        @Valid
        @Size(min = 1, max = 10)
        List<LoanCorrectionTaskRequest> tasks
) {
    public LoanCorrectionPlan toPlan() {
        return new LoanCorrectionPlan(tasks == null ? null : tasks.stream()
                .map(task -> task == null ? null : task.toTask()).toList());
    }
}

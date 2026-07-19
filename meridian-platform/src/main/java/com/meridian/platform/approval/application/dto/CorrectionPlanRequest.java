package com.meridian.platform.approval.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CorrectionPlanRequest(
        @Valid
        @Size(min = 1, max = 10)
        List<CorrectionTaskRequest> tasks
) {
}

package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CorrectionResubmissionDto(
        UUID correctionRequestId,
        UUID loanApplicationId,
        String loanApplicationStatus,
        UUID resubmissionRequestId,
        LocalDateTime resubmittedAt
) {
}

package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffDisbursementCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        LoanContractDto currentContract,
        StaffDisbursementActivationDto activation,
        String workStage
) {
}

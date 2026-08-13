package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CollateralLoanApplicationDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        String status,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String collateralType,
        String productVerificationResult,
        List<SubmissionEvidenceRequirementDto> evidenceRequirements,
        LocalDateTime submittedAt
) {
    public CollateralLoanApplicationDto {
        evidenceRequirements = List.copyOf(evidenceRequirements);
    }
}

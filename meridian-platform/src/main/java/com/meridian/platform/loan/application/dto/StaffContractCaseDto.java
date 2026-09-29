package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffContractCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        String originationChannel,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        LoanContractDto currentContract,
        ContractReadinessDto readiness,
        AssistedActionEvidenceMetadataDto assistedAcknowledgmentEvidence,
        String workStage,
        AccountingCaseContextDto accountingContext
) {
}

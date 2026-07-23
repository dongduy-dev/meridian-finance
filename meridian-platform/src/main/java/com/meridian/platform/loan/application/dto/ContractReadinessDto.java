package com.meridian.platform.loan.application.dto;

import java.util.List;
import java.util.UUID;

public record ContractReadinessDto(
        UUID loanApplicationId,
        UUID contractId,
        Integer contractVersion,
        boolean ready,
        List<String> blockerCodes,
        String calculationSemantics,
        boolean recomputedDuringConfirmation
) {
    public ContractReadinessDto {
        blockerCodes = List.copyOf(blockerCodes);
    }
}

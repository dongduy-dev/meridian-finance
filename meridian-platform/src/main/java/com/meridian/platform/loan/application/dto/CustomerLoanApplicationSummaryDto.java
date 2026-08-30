package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerLoanApplicationSummaryDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String status,
        LocalDateTime submittedAt,
        boolean lifecycleActive,
        CustomerApplicationAction requiredAction
) {
    public enum CustomerApplicationAction {
        UPLOAD_DOCUMENTS,
        COMPLETE_CORRECTIONS,
        REVIEW_APPROVED_OFFER,
        ACKNOWLEDGE_CONTRACT,
        NONE
    }
}

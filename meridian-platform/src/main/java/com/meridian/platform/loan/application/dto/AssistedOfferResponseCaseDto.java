package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssistedOfferResponseCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        String originationChannel,
        String applicationStatus,
        LocalDateTime submittedAt,
        ApprovedOfferDto approvedOffer,
        AssistedActionEvidenceMetadataDto evidence,
        String workState
) {
}

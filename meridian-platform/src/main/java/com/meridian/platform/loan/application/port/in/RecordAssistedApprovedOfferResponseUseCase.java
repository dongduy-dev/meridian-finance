package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.domain.model.CustomerOfferDecision;

import java.util.UUID;

public interface RecordAssistedApprovedOfferResponseUseCase {

    ApprovedOfferActionResult record(Command command);

    record Command(
            UUID requestId,
            UUID loanApplicationId,
            UUID expectedApprovedOfferId,
            CustomerOfferDecision action,
            UUID evidenceDocumentVersionId
    ) {
    }
}

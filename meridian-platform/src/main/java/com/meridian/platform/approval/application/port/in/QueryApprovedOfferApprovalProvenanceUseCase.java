package com.meridian.platform.approval.application.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

public interface QueryApprovedOfferApprovalProvenanceUseCase {
    ApprovedOfferApprovalProvenance requireExactApproval(UUID loanApplicationId, LocalDateTime offerGeneratedAt);
}

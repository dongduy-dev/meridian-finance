package com.meridian.platform.loan.application.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ApprovedOfferApprovalProvenancePort {
    ApprovedOfferApprovalSnapshot requireExactApproval(UUID loanApplicationId, LocalDateTime offerGeneratedAt);
}

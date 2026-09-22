package com.meridian.platform.loan.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record StaffAssistedContractAcknowledgment(
        UUID id,
        UUID acknowledgmentRequestId,
        UUID loanApplicationId,
        UUID customerId,
        UUID loanContractId,
        int contractVersion,
        UUID evidenceDocumentVersionId,
        UUID recordedByStaffUserId,
        LocalDateTime recordedAt
) {
    public StaffAssistedContractAcknowledgment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(acknowledgmentRequestId);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(loanContractId);
        if (contractVersion <= 0) throw new IllegalArgumentException("contractVersion must be positive");
        Objects.requireNonNull(evidenceDocumentVersionId);
        Objects.requireNonNull(recordedByStaffUserId);
        Objects.requireNonNull(recordedAt);
    }
}

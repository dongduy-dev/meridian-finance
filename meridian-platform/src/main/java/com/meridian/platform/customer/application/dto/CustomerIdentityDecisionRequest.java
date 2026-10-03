package com.meridian.platform.customer.application.dto;

import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.RejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CustomerIdentityDecisionRequest(@NotNull UUID requestId, @NotNull UUID documentVersionId,
        @Size(max = 100) String presentedIdentityReference, RejectionReason rejectionReason) {
    @Override public String toString() { return "CustomerIdentityDecisionRequest[requestId=" + requestId + "]"; }
}

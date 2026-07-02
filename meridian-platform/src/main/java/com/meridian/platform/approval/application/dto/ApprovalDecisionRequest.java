package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @NotNull
        ApprovalDecisionAction action,

        @Size(max = 2000)
        String reason,

        @Size(max = 2000)
        String internalNotes
) {
}

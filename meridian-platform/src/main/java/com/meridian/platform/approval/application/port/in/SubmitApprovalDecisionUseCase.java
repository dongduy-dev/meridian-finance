package com.meridian.platform.approval.application.port.in;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;

import java.util.UUID;

public interface SubmitApprovalDecisionUseCase {

    ApprovalDecisionDto submitApprovalDecision(
            UUID loanApplicationId,
            ApprovalDecisionRequest request
    );
}

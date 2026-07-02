package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.domain.model.ApprovalDecision;

public interface ApprovalDecisionRepository {

    ApprovalDecision save(ApprovalDecision approvalDecision);
}

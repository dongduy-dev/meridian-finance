package com.meridian.platform.testsupport;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.loan.application.dto.LoanCorrectionPlanRequest;
import com.meridian.platform.loan.application.dto.LoanCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanDocumentType;

public final class CorrectionRequestFixtures {
    private CorrectionRequestFixtures() {
    }

    public static LoanCorrectionPlanRequest loanRequest(CorrectionPlanRequest plan) {
        return new LoanCorrectionPlanRequest(plan.tasks().stream().map(task -> new LoanCorrectionTaskRequest(
                CorrectionScope.valueOf(task.scope().name()),
                CorrectionResponsibility.valueOf(task.responsibleParty().name()),
                LoanDocumentType.valueOf(task.documentType().name()), task.createChecklistItem(),
                task.checklistItemId(), task.baselineDocumentVersionId(),
                task.customerInstruction(), task.staffInstruction())).toList());
    }
}

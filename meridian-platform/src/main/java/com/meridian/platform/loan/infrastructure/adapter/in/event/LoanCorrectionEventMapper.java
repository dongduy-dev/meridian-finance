package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.CorrectionPlanFacts;
import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan;
import com.meridian.platform.loan.domain.model.LoanDocumentType;

final class LoanCorrectionEventMapper {
    private LoanCorrectionEventMapper() {
    }

    static LoanCorrectionPlan toPlan(CorrectionPlanFacts facts) {
        if (facts == null) return null;
        return new LoanCorrectionPlan(facts.tasks() == null ? null : facts.tasks().stream()
                .map(task -> task == null ? null : new LoanCorrectionPlan.Task(
                        task.scope() == null ? null : CorrectionScope.valueOf(task.scope()),
                        task.responsibleParty() == null ? null : CorrectionResponsibility.valueOf(task.responsibleParty()),
                        task.documentType() == null ? null : LoanDocumentType.valueOf(task.documentType()),
                        task.createChecklistItem(), task.checklistItemId(), task.baselineDocumentVersionId(),
                        task.customerInstruction(), task.staffInstruction()))
                .toList());
    }
}

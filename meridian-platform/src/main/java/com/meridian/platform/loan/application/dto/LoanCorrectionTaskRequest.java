package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CorrectionTaskRequest")
public record LoanCorrectionTaskRequest(
        CorrectionScope scope,
        CorrectionResponsibility responsibleParty,
        LoanDocumentType documentType,
        boolean createChecklistItem,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        @Size(max = 500) String customerInstruction,
        @Size(max = 500) String staffInstruction
) {
    public LoanCorrectionPlan.Task toTask() {
        return new LoanCorrectionPlan.Task(scope, responsibleParty, documentType, createChecklistItem,
                checklistItemId, baselineDocumentVersionId, customerInstruction, staffInstruction);
    }
}

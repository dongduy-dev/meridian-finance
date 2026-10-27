package com.meridian.platform.loan.application.dto.correction;

import com.meridian.platform.loan.domain.model.LoanDocumentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record LoanCorrectionPlan(List<Task> tasks) {
    public LoanCorrectionPlan {
        tasks = tasks == null ? null : Collections.unmodifiableList(new ArrayList<>(tasks));
    }

    public record Task(
            CorrectionScope scope,
            CorrectionResponsibility responsibleParty,
            LoanDocumentType documentType,
            boolean createChecklistItem,
            UUID checklistItemId,
            UUID baselineDocumentVersionId,
            String customerInstruction,
            String staffInstruction
    ) {
    }
}

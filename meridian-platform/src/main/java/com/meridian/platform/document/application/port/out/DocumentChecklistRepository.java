package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;

import java.util.Optional;
import java.util.UUID;

public interface DocumentChecklistRepository {

    DocumentChecklist save(DocumentChecklist checklist);

    DocumentChecklistItem saveItem(DocumentChecklistItem item);

    Optional<DocumentChecklist> findByLoanApplicationIdAndStage(
            UUID loanApplicationId,
            DocumentChecklistStage stage
    );

    Optional<DocumentChecklistItem> findItemById(UUID checklistItemId);

    Optional<DocumentChecklistItem> findItemByIdForUpdate(UUID checklistItemId);

    DocumentChecklistReadiness findReadiness(UUID loanApplicationId, DocumentChecklistStage stage);
}

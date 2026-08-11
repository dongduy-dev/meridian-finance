package com.meridian.platform.document.application.service;

import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class UnsecuredConsumerLoanDocumentChecklistResolver {

    public List<DocumentChecklistItem> resolve(
            UUID checklistId,
            ProductCode productCode,
            LocalDateTime createdAt
    ) {
        if (productCode != ProductCode.UNSECURED_CONSUMER_LOAN) {
            throw new IllegalArgumentException("Unsupported document checklist product.");
        }
        return List.of(
                requiredItem(checklistId, DocumentType.INCOME_PROOF, createdAt),
                requiredItem(checklistId, DocumentType.BANK_STATEMENT, createdAt),
                requiredItem(checklistId, DocumentType.EMPLOYMENT_PROOF, createdAt)
        );
    }

    private DocumentChecklistItem requiredItem(
            UUID checklistId,
            DocumentType documentType,
            LocalDateTime createdAt
    ) {
        return new DocumentChecklistItem(
                UUID.randomUUID(),
                checklistId,
                documentType,
                DocumentRequirementStatus.REQUIRED,
                null,
                createdAt,
                createdAt
        );
    }
}

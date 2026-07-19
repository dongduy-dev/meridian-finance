package com.meridian.platform.document.application.service;

import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class SalaryAdvanceDocumentChecklistResolver {

    public List<DocumentChecklistItem> resolve(
            UUID checklistId,
            ProductCode productCode,
            LocalDateTime createdAt
    ) {
        if (productCode != ProductCode.SALARY_ADVANCE) {
            throw new IllegalArgumentException("Unsupported document checklist product.");
        }
        return List.of();
    }
}

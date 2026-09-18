package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoanIntakeEvidenceService implements LoanIntakeEvidencePort {

    private final IntakeDocumentRepository documents;

    public LoanIntakeEvidenceService(IntakeDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public void requireCurrentUclPaperApplication(UUID assistedOriginationCaseId) {
        boolean present = documents.findByCaseAndTypeForUpdate(
                        assistedOriginationCaseId, IntakeEvidenceType.UCL_PAPER_APPLICATION)
                .filter(document -> document.currentVersionId() != null)
                .isPresent();
        if (!present) {
            throw new BusinessRuleViolationException(
                    "UCL_PAPER_APPLICATION_REQUIRED",
                    "A current signed UCL paper application is required before conversion."
            );
        }
    }
}

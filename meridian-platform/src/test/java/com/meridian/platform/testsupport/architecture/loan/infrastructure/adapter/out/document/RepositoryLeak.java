package com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;

import java.util.UUID;

public class RepositoryLeak implements LoanIntakeEvidencePort {
    private final IntakeDocumentRepository repository;

    public RepositoryLeak(IntakeDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void requireCurrentUclPaperApplication(UUID caseId) {
        repository.findByCase(caseId);
    }

    @Override
    public void requireCurrentCollateralPaperApplication(UUID caseId) {
        repository.findByCase(caseId);
    }
}

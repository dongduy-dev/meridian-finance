package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.LoanIntakeEvidenceUseCase;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoanIntakeEvidenceAdapter implements LoanIntakeEvidencePort {

    private final LoanIntakeEvidenceUseCase evidence;

    public LoanIntakeEvidenceAdapter(LoanIntakeEvidenceUseCase evidence) {
        this.evidence = evidence;
    }

    @Override
    public void requireCurrentUclPaperApplication(UUID assistedOriginationCaseId) {
        evidence.requireCurrentUclPaperApplication(assistedOriginationCaseId);
    }

    @Override
    public void requireCurrentCollateralPaperApplication(UUID assistedOriginationCaseId) {
        evidence.requireCurrentCollateralPaperApplication(assistedOriginationCaseId);
    }
}

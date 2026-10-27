package com.meridian.platform.document.application.port.in;

import java.util.UUID;

public interface LoanIntakeEvidenceUseCase {

    void requireCurrentUclPaperApplication(UUID assistedOriginationCaseId);

    void requireCurrentCollateralPaperApplication(UUID assistedOriginationCaseId);
}

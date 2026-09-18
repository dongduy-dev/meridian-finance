package com.meridian.platform.loan.application.port.out;

import java.util.UUID;

public interface LoanIntakeEvidencePort {

    void requireCurrentUclPaperApplication(UUID assistedOriginationCaseId);
}

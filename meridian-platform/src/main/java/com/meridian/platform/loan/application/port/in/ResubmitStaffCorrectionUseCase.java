package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CorrectionResubmissionDto;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;

import java.util.UUID;

public interface ResubmitStaffCorrectionUseCase {
    CorrectionResubmissionDto resubmitAsStaff(
            UUID loanApplicationId,
            CorrectionResubmissionRequest request
    );
}

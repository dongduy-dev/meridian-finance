package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffLoanAccountServicingProvenanceDto;
import java.util.UUID;

public interface QueryStaffLoanAccountServicingProvenanceUseCase {
    StaffLoanAccountServicingProvenanceDto query(UUID loanApplicationId, int page, int size);
}

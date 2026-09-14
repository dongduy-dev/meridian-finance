package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffApprovedSettlementEvidenceDto;

import java.util.UUID;

public interface QueryStaffApprovedSettlementEvidenceUseCase {
    StaffApprovedSettlementEvidenceDto query(UUID loanApplicationId);
}

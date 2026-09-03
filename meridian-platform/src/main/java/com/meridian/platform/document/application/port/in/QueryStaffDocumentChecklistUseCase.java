package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.StaffDocumentChecklistDto;

import java.util.UUID;

public interface QueryStaffDocumentChecklistUseCase {
    StaffDocumentChecklistDto query(UUID loanApplicationId);
}

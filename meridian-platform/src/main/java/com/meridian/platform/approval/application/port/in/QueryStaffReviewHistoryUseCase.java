package com.meridian.platform.approval.application.port.in;

import com.meridian.platform.approval.application.dto.StaffReviewHistoryDto;

import java.util.UUID;

public interface QueryStaffReviewHistoryUseCase {
    StaffReviewHistoryDto query(UUID loanApplicationId);
}

package com.meridian.platform.approval.application.port.in;

import com.meridian.platform.approval.application.dto.StaffApprovalQueuePageDto;
import com.meridian.platform.approval.application.dto.StaffDecisionCaseDto;
import com.meridian.platform.approval.application.dto.StaffRecommendationCaseDto;

import java.util.UUID;

public interface QueryStaffApprovalWorkUseCase {

    StaffRecommendationCaseDto queryRecommendationCase(UUID loanApplicationId);

    StaffDecisionCaseDto queryDecisionCase(UUID loanApplicationId);

    StaffApprovalQueuePageDto queryDecisionQueue(String productCode, int page, int size);
}

package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.StaffApprovalQueuePageDto;
import com.meridian.platform.approval.application.dto.StaffDecisionCaseDto;
import com.meridian.platform.approval.application.dto.StaffRecommendationCaseDto;
import com.meridian.platform.approval.application.port.in.QueryStaffApprovalWorkUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffApprovalWorkController {

    private final QueryStaffApprovalWorkUseCase queryStaffApprovalWorkUseCase;

    public StaffApprovalWorkController(QueryStaffApprovalWorkUseCase queryStaffApprovalWorkUseCase) {
        this.queryStaffApprovalWorkUseCase = queryStaffApprovalWorkUseCase;
    }

    @GetMapping("/loan-applications/{loanApplicationId}/recommendation")
    @PreAuthorize("hasAuthority('approval:recommend')")
    public StaffRecommendationCaseDto queryRecommendationCase(
            @PathVariable UUID loanApplicationId
    ) {
        return queryStaffApprovalWorkUseCase.queryRecommendationCase(loanApplicationId);
    }

    @GetMapping("/loan-applications/{loanApplicationId}/decision")
    @PreAuthorize("hasAuthority('approval:decide')")
    public StaffDecisionCaseDto queryDecisionCase(@PathVariable UUID loanApplicationId) {
        return queryStaffApprovalWorkUseCase.queryDecisionCase(loanApplicationId);
    }

    @GetMapping("/approval-work")
    @PreAuthorize("hasAuthority('approval:decide')")
    public StaffApprovalQueuePageDto queryDecisionQueue(
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return queryStaffApprovalWorkUseCase.queryDecisionQueue(productCode, page, size);
    }
}

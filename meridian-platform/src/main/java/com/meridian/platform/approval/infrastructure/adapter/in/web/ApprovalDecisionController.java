package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/approval-decisions")
public class ApprovalDecisionController {

    private final SubmitApprovalDecisionUseCase submitApprovalDecisionUseCase;

    public ApprovalDecisionController(SubmitApprovalDecisionUseCase submitApprovalDecisionUseCase) {
        this.submitApprovalDecisionUseCase = submitApprovalDecisionUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('approval:decide')")
    public ApprovalDecisionDto submitApprovalDecision(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return submitApprovalDecisionUseCase.submitApprovalDecision(loanApplicationId, request);
    }
}

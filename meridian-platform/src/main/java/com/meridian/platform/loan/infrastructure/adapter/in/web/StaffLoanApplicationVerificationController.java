package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationVerificationUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/verification")
public class StaffLoanApplicationVerificationController {

    private final QueryStaffLoanApplicationVerificationUseCase queryVerification;

    public StaffLoanApplicationVerificationController(
            QueryStaffLoanApplicationVerificationUseCase queryVerification
    ) {
        this.queryVerification = queryVerification;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:review')")
    public StaffLoanApplicationVerificationDto query(@PathVariable UUID loanApplicationId) {
        return queryVerification.query(loanApplicationId);
    }
}

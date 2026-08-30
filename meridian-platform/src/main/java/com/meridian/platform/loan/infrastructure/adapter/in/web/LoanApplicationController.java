package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerLoanApplicationSummaryDto;
import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications")
public class LoanApplicationController {

    private final QueryLoanApplicationUseCase queryLoanApplication;

    public LoanApplicationController(QueryLoanApplicationUseCase queryLoanApplication) {
        this.queryLoanApplication = queryLoanApplication;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read:own')")
    public List<CustomerLoanApplicationSummaryDto> queryOwnApplications() {
        return queryLoanApplication.queryOwnApplications();
    }

    @GetMapping("/{loanApplicationId}")
    @PreAuthorize("hasAnyAuthority('loan:read:own', 'loan:read')")
    public LoanApplicationStatusDto query(@PathVariable UUID loanApplicationId) {
        return queryLoanApplication.query(loanApplicationId);
    }
}

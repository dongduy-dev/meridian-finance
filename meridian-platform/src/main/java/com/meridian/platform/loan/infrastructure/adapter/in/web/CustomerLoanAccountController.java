package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerLoanAccountSummaryDto;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loan-accounts")
public class CustomerLoanAccountController {

    private final QueryLoanAccountUseCase queryLoanAccounts;

    public CustomerLoanAccountController(QueryLoanAccountUseCase queryLoanAccounts) {
        this.queryLoanAccounts = queryLoanAccounts;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read:own')")
    public List<CustomerLoanAccountSummaryDto> queryOwnAccounts() {
        return queryLoanAccounts.queryOwnAccounts();
    }
}

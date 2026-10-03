package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanAccountCustomerContextDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountCustomerContextUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/servicing-context")
public class StaffLoanAccountCustomerContextController {
    private final QueryStaffLoanAccountCustomerContextUseCase query;

    public StaffLoanAccountCustomerContextController(QueryStaffLoanAccountCustomerContextUseCase query) {
        this.query = query;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read')")
    public StaffLoanAccountCustomerContextDto query(@PathVariable UUID loanApplicationId) {
        return query.query(loanApplicationId);
    }
}

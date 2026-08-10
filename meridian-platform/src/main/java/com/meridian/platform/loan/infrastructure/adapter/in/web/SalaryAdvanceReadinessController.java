package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.SalaryAdvanceReadinessDto;
import com.meridian.platform.loan.application.port.in.QuerySalaryAdvanceReadinessUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-products/salary-advance/readiness")
public class SalaryAdvanceReadinessController {

    private final QuerySalaryAdvanceReadinessUseCase queryReadiness;

    public SalaryAdvanceReadinessController(QuerySalaryAdvanceReadinessUseCase queryReadiness) {
        this.queryReadiness = queryReadiness;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:submit')")
    public SalaryAdvanceReadinessDto readiness() {
        return queryReadiness.queryReadiness();
    }
}

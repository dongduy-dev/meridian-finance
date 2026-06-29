package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-applications/salary-advance")
public class SalaryAdvanceLoanApplicationController {

    private final StartSalaryAdvanceApplicationUseCase startSalaryAdvanceApplicationUseCase;

    public SalaryAdvanceLoanApplicationController(
            StartSalaryAdvanceApplicationUseCase startSalaryAdvanceApplicationUseCase
    ) {
        this.startSalaryAdvanceApplicationUseCase = startSalaryAdvanceApplicationUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan:submit')")
    public SalaryAdvanceApplicationDto startSalaryAdvanceApplication(
            @Valid @RequestBody SalaryAdvanceApplicationRequest request
    ) {
        return startSalaryAdvanceApplicationUseCase.startSalaryAdvanceApplication(request);
    }
}

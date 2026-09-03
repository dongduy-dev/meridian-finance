package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffCorrectionCaseDto;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionCaseUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/corrections")
public class StaffCorrectionReadController {
    private final QueryStaffCorrectionCaseUseCase queryCase;

    public StaffCorrectionReadController(QueryStaffCorrectionCaseUseCase queryCase) {
        this.queryCase = queryCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:correction:staff')")
    public StaffCorrectionCaseDto query(@PathVariable UUID loanApplicationId) {
        return queryCase.query(loanApplicationId);
    }
}

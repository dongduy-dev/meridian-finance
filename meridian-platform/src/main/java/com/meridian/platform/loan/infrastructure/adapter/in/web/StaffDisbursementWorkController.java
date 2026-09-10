package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffDisbursementCaseDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffDisbursementWorkUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/staff")
public class StaffDisbursementWorkController {

    private final QueryStaffDisbursementWorkUseCase queryStaffDisbursementWork;

    public StaffDisbursementWorkController(
            QueryStaffDisbursementWorkUseCase queryStaffDisbursementWork
    ) {
        this.queryStaffDisbursementWork = queryStaffDisbursementWork;
    }

    @GetMapping("/disbursement-work")
    @PreAuthorize("hasAuthority('loan:disburse')")
    public StaffDisbursementWorkPageDto queryWork(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return queryStaffDisbursementWork.queryWork(productCode, page, size);
    }

    @GetMapping("/loan-applications/{loanApplicationId}/disbursement")
    @PreAuthorize("hasAuthority('loan:disburse')")
    public StaffDisbursementCaseDto queryCase(@PathVariable UUID loanApplicationId) {
        return queryStaffDisbursementWork.queryCase(loanApplicationId);
    }
}

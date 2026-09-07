package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffContractCaseDto;
import com.meridian.platform.loan.application.dto.StaffContractWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffContractWorkUseCase;
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
public class StaffContractWorkController {

    private final QueryStaffContractWorkUseCase queryStaffContractWork;

    public StaffContractWorkController(QueryStaffContractWorkUseCase queryStaffContractWork) {
        this.queryStaffContractWork = queryStaffContractWork;
    }

    @GetMapping("/contract-work")
    @PreAuthorize("hasAuthority('loan:contract:read')")
    public StaffContractWorkPageDto queryWork(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return queryStaffContractWork.queryWork(productCode, page, size);
    }

    @GetMapping("/loan-applications/{loanApplicationId}/contract")
    @PreAuthorize("hasAuthority('loan:contract:read')")
    public StaffContractCaseDto queryCase(@PathVariable UUID loanApplicationId) {
        return queryStaffContractWork.queryCase(loanApplicationId);
    }
}

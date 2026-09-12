package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffServicingWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffServicingWorkUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/staff/servicing-work")
public class StaffServicingWorkController {

    private final QueryStaffServicingWorkUseCase queryStaffServicingWork;

    public StaffServicingWorkController(
            QueryStaffServicingWorkUseCase queryStaffServicingWork
    ) {
        this.queryStaffServicingWork = queryStaffServicingWork;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read')")
    public StaffServicingWorkPageDto queryWork(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(required = false) LoanAccountStatus accountStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return queryStaffServicingWork.queryWork(productCode, accountStatus, page, size);
    }
}

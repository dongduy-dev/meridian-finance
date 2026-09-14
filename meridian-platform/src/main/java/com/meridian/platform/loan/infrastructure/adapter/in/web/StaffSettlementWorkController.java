package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffSettlementWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffSettlementWorkUseCase;
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
@RequestMapping("/api/v1/staff/settlement-work")
public class StaffSettlementWorkController {
    private final QueryStaffSettlementWorkUseCase query;

    public StaffSettlementWorkController(QueryStaffSettlementWorkUseCase query) {
        this.query = query;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:settlement:approve')")
    public StaffSettlementWorkPageDto queryWork(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return query.queryWork(productCode, page, size);
    }
}

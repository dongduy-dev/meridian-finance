package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanAccountServicingProvenanceDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountServicingProvenanceUseCase;
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
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/servicing-provenance")
public class StaffLoanAccountServicingProvenanceController {
    private final QueryStaffLoanAccountServicingProvenanceUseCase query;

    public StaffLoanAccountServicingProvenanceController(
            QueryStaffLoanAccountServicingProvenanceUseCase query) {
        this.query = query;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read')")
    public StaffLoanAccountServicingProvenanceDto query(
            @PathVariable UUID loanApplicationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return query.query(loanApplicationId, page, size);
    }
}

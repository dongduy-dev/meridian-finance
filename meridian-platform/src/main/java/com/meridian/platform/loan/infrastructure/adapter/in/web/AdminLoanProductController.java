package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.dto.ChangeLoanProductActivationRequest;
import com.meridian.platform.loan.application.dto.UpdateLoanProductLimitsRequest;
import com.meridian.platform.loan.application.port.in.ManageLoanProductUseCase;
import com.meridian.platform.loan.application.port.in.QueryAdminLoanProductsUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/loan-products")
public class AdminLoanProductController {
    private final QueryAdminLoanProductsUseCase query;
    private final ManageLoanProductUseCase commands;

    public AdminLoanProductController(QueryAdminLoanProductsUseCase query, ManageLoanProductUseCase commands) {
        this.query = query;
        this.commands = commands;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:product:manage')")
    public List<AdminLoanProductDto> getLoanProducts() {
        return query.findAll();
    }

    @PutMapping("/{productCode}/limits")
    @PreAuthorize("hasAuthority('loan:product:manage')")
    public AdminLoanProductDto updateLimits(
            @PathVariable String productCode,
            @Valid @RequestBody UpdateLoanProductLimitsRequest request
    ) {
        return commands.updateLimits(productCode, request);
    }

    @PutMapping("/{productCode}/activation")
    @PreAuthorize("hasAuthority('loan:product:manage')")
    public AdminLoanProductDto changeActivation(
            @PathVariable String productCode,
            @Valid @RequestBody ChangeLoanProductActivationRequest request
    ) {
        return commands.changeActivation(productCode, request);
    }
}

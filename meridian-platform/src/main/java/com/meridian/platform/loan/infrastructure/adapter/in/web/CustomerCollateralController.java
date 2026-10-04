package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerCollateralDto;
import com.meridian.platform.loan.application.port.in.QueryOwnCollateralUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/collateral")
public class CustomerCollateralController {

    private final QueryOwnCollateralUseCase queryOwnCollateral;

    public CustomerCollateralController(QueryOwnCollateralUseCase queryOwnCollateral) {
        this.queryOwnCollateral = queryOwnCollateral;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read:own')")
    public CustomerCollateralDto query(@PathVariable UUID loanApplicationId) {
        return queryOwnCollateral.query(loanApplicationId);
    }
}

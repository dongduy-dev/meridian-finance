package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/loan-applications/{loanApplicationId}/collateral-loan-verification"
)
public class CollateralLoanVerificationController {

    private final ManageCollateralLoanVerificationUseCase useCase;

    public CollateralLoanVerificationController(ManageCollateralLoanVerificationUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('loan:review')")
    public CollateralLoanVerificationStartDto startManualVerification(
            @PathVariable UUID loanApplicationId
    ) {
        return useCase.startManualVerification(loanApplicationId);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('loan:review')")
    public CollateralLoanVerificationDto completeManualVerification(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CompleteCollateralLoanVerificationRequest request
    ) {
        return useCase.completeManualVerification(loanApplicationId, request);
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
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
        "/api/v1/loan-applications/{loanApplicationId}/unsecured-consumer-loan-verification"
)
public class UnsecuredConsumerLoanVerificationController {

    private final ManageUnsecuredConsumerLoanVerificationUseCase useCase;

    public UnsecuredConsumerLoanVerificationController(
            ManageUnsecuredConsumerLoanVerificationUseCase useCase
    ) {
        this.useCase = useCase;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('loan:review')")
    public UnsecuredConsumerLoanVerificationDto startManualVerification(
            @PathVariable UUID loanApplicationId
    ) {
        return useCase.startManualVerification(loanApplicationId);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('loan:review')")
    public UnsecuredConsumerLoanVerificationDto completeManualVerification(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CompleteUnsecuredConsumerLoanVerificationRequest request
    ) {
        return useCase.completeManualVerification(loanApplicationId, request);
    }
}

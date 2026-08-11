package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-applications/unsecured-consumer-loan")
public class UnsecuredConsumerLoanApplicationController {

    private final StartUnsecuredConsumerLoanApplicationUseCase useCase;

    public UnsecuredConsumerLoanApplicationController(
            StartUnsecuredConsumerLoanApplicationUseCase useCase
    ) {
        this.useCase = useCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan:submit')")
    public UnsecuredConsumerLoanApplicationDto startUnsecuredConsumerLoanApplication(
            @Valid @RequestBody UnsecuredConsumerLoanApplicationRequest request
    ) {
        return useCase.startUnsecuredConsumerLoanApplication(request);
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CancelLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.CancelledLoanApplicationDto;
import com.meridian.platform.loan.application.mapper.LoanApplicationCancellationApiMapper;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/cancel")
public class LoanApplicationCancellationController {

    private final CancelLoanApplicationUseCase cancellations;
    private final LoanApplicationCancellationApiMapper mapper;

    public LoanApplicationCancellationController(
            CancelLoanApplicationUseCase cancellations,
            LoanApplicationCancellationApiMapper mapper
    ) {
        this.cancellations = cancellations;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:cancel:own')")
    public CancelledLoanApplicationDto cancel(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CancelLoanApplicationRequest request
    ) {
        return mapper.toDto(cancellations.cancel(new CancelLoanApplicationUseCase.Command(
                request.requestId(),
                loanApplicationId
        )));
    }
}

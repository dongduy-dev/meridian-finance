package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CloseLoanAccountRequest;
import com.meridian.platform.loan.application.dto.ClosedLoanAccountDto;
import com.meridian.platform.loan.application.mapper.LoanAccountClosureApiMapper;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
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
@RequestMapping(
        "/api/v1/loan-applications/{loanApplicationId}/loan-account/closure"
)
public class LoanAccountClosureController {

    private final CloseLoanAccountUseCase closures;
    private final LoanAccountClosureApiMapper mapper;

    public LoanAccountClosureController(
            CloseLoanAccountUseCase closures,
            LoanAccountClosureApiMapper mapper
    ) {
        this.closures = closures;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:account:close')")
    public ClosedLoanAccountDto close(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CloseLoanAccountRequest request
    ) {
        return mapper.toDto(closures.close(new CloseLoanAccountUseCase.Command(
                request.requestId(),
                loanApplicationId
        )));
    }
}

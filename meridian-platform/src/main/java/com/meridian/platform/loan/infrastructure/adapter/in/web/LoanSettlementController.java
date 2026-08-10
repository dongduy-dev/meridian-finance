package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ApproveLoanSettlementRequest;
import com.meridian.platform.loan.application.dto.ApprovedLoanSettlementDto;
import com.meridian.platform.loan.application.mapper.LoanSettlementApiMapper;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
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
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/settlements")
public class LoanSettlementController {

    private final ApproveLoanSettlementUseCase settlements;
    private final LoanSettlementApiMapper mapper;

    public LoanSettlementController(
            ApproveLoanSettlementUseCase settlements,
            LoanSettlementApiMapper mapper
    ) {
        this.settlements = settlements;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:settlement:approve')")
    public ApprovedLoanSettlementDto approve(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ApproveLoanSettlementRequest request
    ) {
        return mapper.toDto(settlements.approve(
                new ApproveLoanSettlementUseCase.Command(
                        request.requestId(),
                        loanApplicationId,
                        request.expectedSettlementAmount(),
                        request.paymentValueDate(),
                        request.externalPaymentReference()
                )
        ));
    }
}

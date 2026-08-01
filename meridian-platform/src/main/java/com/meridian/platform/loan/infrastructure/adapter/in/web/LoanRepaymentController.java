package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.RecordRepaymentDto;
import com.meridian.platform.loan.application.dto.RecordRepaymentRequest;
import com.meridian.platform.loan.application.dto.RepaymentHistoryPageDto;
import com.meridian.platform.loan.application.mapper.LoanRepaymentApiMapper;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/repayments")
public class LoanRepaymentController {

    private final RecordRepaymentUseCase recordRepayment;
    private final QueryRepaymentsUseCase queryRepayments;
    private final LoanRepaymentApiMapper mapper;

    public LoanRepaymentController(
            RecordRepaymentUseCase recordRepayment,
            QueryRepaymentsUseCase queryRepayments,
            LoanRepaymentApiMapper mapper
    ) {
        this.recordRepayment = recordRepayment;
        this.queryRepayments = queryRepayments;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('repayment:update')")
    public RecordRepaymentDto record(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody RecordRepaymentRequest request
    ) {
        return mapper.toDto(recordRepayment.record(new RecordRepaymentUseCase.Command(
                request.requestId(),
                loanApplicationId,
                request.externalPaymentReference(),
                request.amount(),
                request.paymentValueDate()
        )));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('loan:read:own', 'loan:read')")
    public RepaymentHistoryPageDto history(
            @PathVariable UUID loanApplicationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return mapper.toDto(queryRepayments.query(loanApplicationId, page, size));
    }
}

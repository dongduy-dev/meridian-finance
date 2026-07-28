package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ConfirmManualDisbursementRequest;
import com.meridian.platform.loan.application.dto.DisbursementDestinationRevealDto;
import com.meridian.platform.loan.application.dto.LoanAccountDto;
import com.meridian.platform.loan.application.dto.ManualDisbursementConfirmationDto;
import com.meridian.platform.loan.application.dto.RevealDisbursementDestinationRequest;
import com.meridian.platform.loan.application.mapper.LoanDisbursementApiMapper;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}")
public class LoanDisbursementController {

    private final ConfirmManualDisbursementUseCase confirmManualDisbursement;
    private final RevealDisbursementDestinationUseCase revealDestination;
    private final QueryLoanAccountUseCase queryLoanAccount;
    private final LoanDisbursementApiMapper mapper;

    public LoanDisbursementController(
            ConfirmManualDisbursementUseCase confirmManualDisbursement,
            RevealDisbursementDestinationUseCase revealDestination,
            QueryLoanAccountUseCase queryLoanAccount,
            LoanDisbursementApiMapper mapper
    ) {
        this.confirmManualDisbursement = confirmManualDisbursement;
        this.revealDestination = revealDestination;
        this.queryLoanAccount = queryLoanAccount;
        this.mapper = mapper;
    }

    @PostMapping("/disbursements")
    @PreAuthorize("hasAuthority('loan:disburse')")
    public ManualDisbursementConfirmationDto confirm(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ConfirmManualDisbursementRequest request
    ) {
        return mapper.toDto(confirmManualDisbursement.confirm(
                new ConfirmManualDisbursementUseCase.Command(
                        request.requestId(),
                        loanApplicationId,
                        request.expectedContractVersion(),
                        request.externalTransferReference(),
                        request.disbursementValueDate(),
                        request.firstRepaymentDate()
                )
        ));
    }

    @PostMapping("/contracts/current/disbursement-destination/reveal")
    @PreAuthorize("hasAuthority('loan:disburse')")
    public ResponseEntity<DisbursementDestinationRevealDto> reveal(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody RevealDisbursementDestinationRequest request
    ) {
        DisbursementDestinationRevealDto response = mapper.toDto(revealDestination.reveal(
                new RevealDisbursementDestinationUseCase.Command(
                        loanApplicationId,
                        request.expectedContractVersion()
                )
        ));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    @GetMapping("/loan-account")
    @PreAuthorize("hasAnyAuthority('loan:read:own', 'loan:read')")
    public LoanAccountDto loanAccount(@PathVariable UUID loanApplicationId) {
        return mapper.toDto(queryLoanAccount.query(loanApplicationId));
    }
}

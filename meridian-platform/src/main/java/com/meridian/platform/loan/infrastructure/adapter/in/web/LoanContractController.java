package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.AcknowledgeLoanContractRequest;
import com.meridian.platform.loan.application.dto.ConfirmContractReadinessRequest;
import com.meridian.platform.loan.application.dto.ContractReadinessDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.PrepareLoanContractRequest;
import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.QueryCurrentLoanContractUseCase;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/contracts")
public class LoanContractController {

    private final PrepareLoanContractUseCase prepareContract;
    private final QueryCurrentLoanContractUseCase queryCurrentContract;
    private final AcknowledgeLoanContractUseCase acknowledgeContract;
    private final QueryContractReadinessUseCase queryReadiness;
    private final ConfirmContractReadinessUseCase confirmReadiness;
    private final LoanContractMapper mapper;

    public LoanContractController(
            PrepareLoanContractUseCase prepareContract,
            QueryCurrentLoanContractUseCase queryCurrentContract,
            AcknowledgeLoanContractUseCase acknowledgeContract,
            QueryContractReadinessUseCase queryReadiness,
            ConfirmContractReadinessUseCase confirmReadiness,
            LoanContractMapper mapper
    ) {
        this.prepareContract = prepareContract;
        this.queryCurrentContract = queryCurrentContract;
        this.acknowledgeContract = acknowledgeContract;
        this.queryReadiness = queryReadiness;
        this.confirmReadiness = confirmReadiness;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:contract:prepare')")
    public LoanContractDto prepare(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody PrepareLoanContractRequest request
    ) {
        return mapper.toDto(prepareContract.prepare(new PrepareLoanContractUseCase.Command(
                request.preparationRequestId(),
                loanApplicationId,
                request.expectedCurrentContractVersion(),
                request.supersessionReasonCode()
        )));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('loan:read:own', 'loan:contract:read')")
    public LoanContractDto current(@PathVariable UUID loanApplicationId) {
        return mapper.toDto(requireCurrent(loanApplicationId));
    }

    @PostMapping("/current/acknowledgment")
    @PreAuthorize("hasAuthority('loan:contract:acknowledge:own')")
    public LoanContractDto acknowledge(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody AcknowledgeLoanContractRequest request
    ) {
        LoanContract current = requireCurrent(loanApplicationId);
        return mapper.toDto(acknowledgeContract.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                request.acknowledgmentRequestId(),
                loanApplicationId,
                current.id(),
                request.expectedContractVersion()
        )));
    }

    @GetMapping("/current/readiness")
    @PreAuthorize("hasAuthority('loan:contract:read')")
    public ContractReadinessDto readiness(
            @PathVariable UUID loanApplicationId,
            @RequestParam(required = false) @Positive Integer expectedContractVersion
    ) {
        return mapper.toDto(queryReadiness.query(loanApplicationId, expectedContractVersion));
    }

    @PostMapping("/current/readiness/confirm")
    @PreAuthorize("hasAuthority('loan:disbursement:prepare')")
    public LoanContractDto confirm(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ConfirmContractReadinessRequest request
    ) {
        LoanContract current = requireCurrent(loanApplicationId);
        return mapper.toDto(confirmReadiness.confirm(new ConfirmContractReadinessUseCase.Command(
                request.confirmationRequestId(),
                loanApplicationId,
                current.id(),
                request.expectedContractVersion()
        )));
    }

    private LoanContract requireCurrent(UUID loanApplicationId) {
        return queryCurrentContract.findCurrent(loanApplicationId).orElseThrow(() -> new EntityNotFoundException(
                "CURRENT_CONTRACT_MISSING",
                "Current loan contract was not found."
        ));
    }
}

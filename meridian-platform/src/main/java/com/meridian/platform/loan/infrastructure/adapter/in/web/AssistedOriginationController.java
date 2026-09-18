package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.AssociateAssistedOriginationCustomerRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.dto.CreateAssistedOriginationCaseRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.ManageAssistedOriginationUseCase;
import com.meridian.platform.loan.application.port.in.StartAssistedCollateralLoanUseCase;
import com.meridian.platform.loan.application.port.in.StartAssistedUnsecuredConsumerLoanUseCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/assisted-originations")
@PreAuthorize("hasAuthority('loan:originate:staff')")
public class AssistedOriginationController {

    private final ManageAssistedOriginationUseCase useCase;
    private final StartAssistedUnsecuredConsumerLoanUseCase assistedUclUseCase;
    private final StartAssistedCollateralLoanUseCase assistedCollateralUseCase;

    public AssistedOriginationController(
            ManageAssistedOriginationUseCase useCase,
            StartAssistedUnsecuredConsumerLoanUseCase assistedUclUseCase,
            StartAssistedCollateralLoanUseCase assistedCollateralUseCase
    ) {
        this.useCase = useCase;
        this.assistedUclUseCase = assistedUclUseCase;
        this.assistedCollateralUseCase = assistedCollateralUseCase;
    }

    @GetMapping
    public List<AssistedOriginationCaseDto> find(
            @RequestParam(required = false) AssistedOriginationCaseStatus status
    ) {
        return useCase.findCases(status);
    }

    @GetMapping("/{caseId}")
    public AssistedOriginationCaseDto get(@PathVariable UUID caseId) {
        return useCase.getCase(caseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssistedOriginationCaseDto create(@Valid @RequestBody CreateAssistedOriginationCaseRequest request) {
        return useCase.createCase(request);
    }

    @PutMapping("/{caseId}/customer")
    public AssistedOriginationCaseDto associateCustomer(
            @PathVariable UUID caseId,
            @Valid @RequestBody AssociateAssistedOriginationCustomerRequest request
    ) {
        return useCase.associateCustomer(caseId, request.customerId());
    }

    @PostMapping("/{caseId}/abandon")
    public AssistedOriginationCaseDto abandon(@PathVariable UUID caseId) {
        return useCase.abandon(caseId);
    }

    @PostMapping("/{caseId}/unsecured-consumer-loan/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistedOriginationCaseDto submitUnsecuredConsumerLoan(
            @PathVariable UUID caseId,
            @Valid @RequestBody UnsecuredConsumerLoanApplicationRequest request
    ) {
        return assistedUclUseCase.submit(caseId, request);
    }

    @PostMapping("/{caseId}/collateral-loan/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistedOriginationCaseDto submitCollateralLoan(
            @PathVariable UUID caseId,
            @Valid @RequestBody CollateralLoanApplicationRequest request
    ) {
        return assistedCollateralUseCase.submit(caseId, request);
    }
}

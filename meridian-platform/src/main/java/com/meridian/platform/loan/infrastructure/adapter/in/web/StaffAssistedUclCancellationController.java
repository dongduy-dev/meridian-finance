package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CancelledLoanApplicationDto;
import com.meridian.platform.loan.application.dto.RecordAssistedUclCancellationRequest;
import com.meridian.platform.loan.application.mapper.LoanApplicationCancellationApiMapper;
import com.meridian.platform.loan.application.port.in.RecordAssistedUclCancellationUseCase;
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
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/cancellation")
public class StaffAssistedUclCancellationController {

    private final RecordAssistedUclCancellationUseCase cancellations;
    private final LoanApplicationCancellationApiMapper mapper;

    public StaffAssistedUclCancellationController(
            RecordAssistedUclCancellationUseCase cancellations,
            LoanApplicationCancellationApiMapper mapper
    ) {
        this.cancellations = cancellations;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:cancel:staff') and hasRole('LOAN_OFFICER')")
    public CancelledLoanApplicationDto record(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody RecordAssistedUclCancellationRequest request
    ) {
        return mapper.toDto(cancellations.record(new RecordAssistedUclCancellationUseCase.Command(
                request.requestId(),
                loanApplicationId,
                request.expectedCorrectionRequestId(),
                request.evidenceDocumentVersionId()
        )));
    }
}

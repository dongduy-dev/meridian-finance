package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.RecordAssistedContractAcknowledgmentRequest;
import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.RecordAssistedLoanContractAcknowledgmentUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/contract/acknowledgment")
public class StaffAssistedContractAcknowledgmentController {

    private final RecordAssistedLoanContractAcknowledgmentUseCase record;
    private final LoanContractMapper mapper;

    public StaffAssistedContractAcknowledgmentController(
            RecordAssistedLoanContractAcknowledgmentUseCase record,
            LoanContractMapper mapper
    ) {
        this.record = record;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('loan:contract:acknowledge:staff')")
    public LoanContractDto record(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody RecordAssistedContractAcknowledgmentRequest request
    ) {
        return mapper.toDto(record.record(new RecordAssistedLoanContractAcknowledgmentUseCase.Command(
                request.acknowledgmentRequestId(), loanApplicationId, request.contractId(),
                request.expectedContractVersion(), request.evidenceDocumentVersionId())));
    }
}

package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.*;
import com.meridian.platform.loan.application.port.in.QueryAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.loan.application.port.in.RecordAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/offer-response")
@PreAuthorize("hasAuthority('loan:offer:respond:staff')")
public class StaffAssistedOfferResponseController {

    private final QueryAssistedApprovedOfferResponseUseCase query;
    private final RecordAssistedApprovedOfferResponseUseCase record;

    public StaffAssistedOfferResponseController(
            QueryAssistedApprovedOfferResponseUseCase query,
            RecordAssistedApprovedOfferResponseUseCase record
    ) {
        this.query = query;
        this.record = record;
    }

    @GetMapping
    public AssistedOfferResponseCaseDto query(@PathVariable UUID loanApplicationId) {
        return query.query(loanApplicationId);
    }

    @PostMapping
    public ApprovedOfferDto record(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody RecordAssistedOfferResponseRequest request
    ) {
        ApprovedOfferActionResult result = record.record(new RecordAssistedApprovedOfferResponseUseCase.Command(
                request.requestId(), loanApplicationId, request.expectedApprovedOfferId(), request.action(),
                request.evidenceDocumentVersionId()));
        if (result.outcome() == ApprovedOfferActionOutcome.EXPIRED) {
            throw new BusinessStateConflictException("OFFER_EXPIRED", "Approved offer has expired.");
        }
        return result.offer();
    }
}

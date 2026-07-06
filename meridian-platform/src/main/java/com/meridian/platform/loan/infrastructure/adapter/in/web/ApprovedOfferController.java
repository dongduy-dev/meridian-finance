package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/approved-offer")
public class ApprovedOfferController {

    private final QueryApprovedOfferUseCase queryApprovedOfferUseCase;
    private final RespondToApprovedOfferUseCase respondToApprovedOfferUseCase;

    public ApprovedOfferController(
            QueryApprovedOfferUseCase queryApprovedOfferUseCase,
            RespondToApprovedOfferUseCase respondToApprovedOfferUseCase
    ) {
        this.queryApprovedOfferUseCase = queryApprovedOfferUseCase;
        this.respondToApprovedOfferUseCase = respondToApprovedOfferUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read:own')")
    public ApprovedOfferDto getApprovedOffer(@PathVariable UUID loanApplicationId) {
        return queryApprovedOfferUseCase.getApprovedOffer(loanApplicationId);
    }

    @PostMapping("/accept")
    @PreAuthorize("hasAuthority('loan:offer:respond:own')")
    public ApprovedOfferDto acceptApprovedOffer(@PathVariable UUID loanApplicationId) {
        return requireNonExpiredResult(respondToApprovedOfferUseCase.acceptOffer(loanApplicationId));
    }

    @PostMapping("/decline")
    @PreAuthorize("hasAuthority('loan:offer:respond:own')")
    public ApprovedOfferDto declineApprovedOffer(@PathVariable UUID loanApplicationId) {
        return requireNonExpiredResult(respondToApprovedOfferUseCase.declineOffer(loanApplicationId));
    }

    private ApprovedOfferDto requireNonExpiredResult(ApprovedOfferActionResult result) {
        if (result.outcome() == ApprovedOfferActionOutcome.EXPIRED) {
            throw new BusinessStateConflictException(
                    "OFFER_EXPIRED",
                    "Approved offer has expired."
            );
        }
        return result.offer();
    }
}

package com.meridian.platform.loan.application.dto;

public record ApprovedOfferActionResult(
        ApprovedOfferActionOutcome outcome,
        ApprovedOfferDto offer
) {
}

package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record CustomerIdentityReferenceRevealDto(UUID loanApplicationId, String identityReference) {
    @Override
    public String toString() {
        return "CustomerIdentityReferenceRevealDto[loanApplicationId=" + loanApplicationId
                + ", identityReference=redacted]";
    }
}

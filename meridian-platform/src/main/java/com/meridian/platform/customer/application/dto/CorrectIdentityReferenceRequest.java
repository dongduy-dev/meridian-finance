package com.meridian.platform.customer.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectIdentityReferenceRequest(@NotBlank @Size(max = 100) String identityReference) {
    @Override
    public String toString() { return "CorrectIdentityReferenceRequest[identityReference=redacted]"; }
}

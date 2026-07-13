package com.meridian.platform.customer.application.port.in;

import java.util.UUID;

public record CustomerIdentityEvidenceSnapshot(
        UUID customerId,
        boolean active,
        boolean profileComplete,
        String identityReference
) {

    @Override
    public String toString() {
        return "CustomerIdentityEvidenceSnapshot[customerId=" + customerId
                + ", active=" + active
                + ", profileComplete=" + profileComplete
                + ", identityReference=redacted]";
    }
}
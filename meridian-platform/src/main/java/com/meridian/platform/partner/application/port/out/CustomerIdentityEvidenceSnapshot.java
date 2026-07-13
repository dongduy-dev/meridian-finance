package com.meridian.platform.partner.application.port.out;

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
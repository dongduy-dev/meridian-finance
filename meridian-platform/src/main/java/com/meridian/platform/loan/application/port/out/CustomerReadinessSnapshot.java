package com.meridian.platform.loan.application.port.out;

import java.util.UUID;

public record CustomerReadinessSnapshot(
        UUID customerId,
        boolean active,
        boolean profileComplete,
        boolean hasPrimaryActiveBankAccount,
        String verificationStatus,
        UUID identityVerificationId
) {
    public CustomerReadinessSnapshot(UUID customerId, boolean active, boolean profileComplete,
            boolean hasPrimaryActiveBankAccount, String verificationStatus) {
        this(customerId, active, profileComplete, hasPrimaryActiveBankAccount, verificationStatus, null);
    }
}
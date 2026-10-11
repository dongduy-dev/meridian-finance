package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.in.BootstrapAdministratorUseCase;
import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import org.springframework.stereotype.Service;

@Service
public class BootstrapAdministratorService implements BootstrapAdministratorUseCase {
    private final ProvisionInternalUserTransactionService transactions;
    private final StaffPasswordSetupNotificationPort notification;

    public BootstrapAdministratorService(ProvisionInternalUserTransactionService transactions,
                                         StaffPasswordSetupNotificationPort notification) {
        this.transactions = transactions;
        this.notification = notification;
    }

    @Override
    public void bootstrap(String email, String displayName) {
        PendingStaffSetupDelivery delivery = transactions.bootstrapAdministrator(email, displayName);
        try {
            notification.sendSetupEmail(delivery.recipientEmail(), delivery.rawToken());
        } catch (RuntimeException exception) {
            // The account and digest have committed. Normal password recovery can replace the token.
            throw new IllegalStateException("Administrator created but setup delivery failed. Disable bootstrap and use password recovery.");
        }
    }
}

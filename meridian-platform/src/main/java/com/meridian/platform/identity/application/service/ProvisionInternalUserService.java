package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.port.in.ProvisionInternalUserUseCase;
import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProvisionInternalUserService implements ProvisionInternalUserUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionInternalUserService.class);

    private final ProvisionInternalUserTransactionService transactions;
    private final StaffPasswordSetupNotificationPort notification;

    public ProvisionInternalUserService(ProvisionInternalUserTransactionService transactions,
                                        StaffPasswordSetupNotificationPort notification) {
        this.transactions = transactions;
        this.notification = notification;
    }

    @Override
    public InternalUserDto create(CreateInternalUserRequest request) {
        PendingStaffSetupDelivery delivery = transactions.create(request);
        deliver(delivery);
        return delivery.user();
    }

    @Override
    public void sendPasswordSetup(UUID userId) {
        deliver(transactions.issueForStaffUser(userId));
    }

    private void deliver(PendingStaffSetupDelivery delivery) {
        try {
            notification.sendSetupEmail(delivery.recipientEmail(), delivery.rawToken());
        } catch (RuntimeException exception) {
            LOGGER.warn("Staff password-setup email delivery failed after token commit.");
        }
    }
}

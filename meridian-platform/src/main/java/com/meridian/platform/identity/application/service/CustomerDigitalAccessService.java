package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;
import com.meridian.platform.identity.application.dto.EnableCustomerDigitalAccessRequest;
import com.meridian.platform.identity.application.port.in.ManageCustomerDigitalAccessUseCase;
import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessInvitationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerDigitalAccessService implements ManageCustomerDigitalAccessUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerDigitalAccessService.class);
    private final CustomerDigitalAccessTransactionService transactions;
    private final CustomerDigitalAccessInvitationPort invitations;

    public CustomerDigitalAccessService(CustomerDigitalAccessTransactionService transactions,
                                        CustomerDigitalAccessInvitationPort invitations) {
        this.transactions = Objects.requireNonNull(transactions);
        this.invitations = Objects.requireNonNull(invitations);
    }

    @Override
    public CustomerDigitalAccessDto status(UUID customerId) {
        return transactions.status(customerId);
    }

    @Override
    public CustomerDigitalAccessDto enable(UUID customerId, EnableCustomerDigitalAccessRequest request) {
        PendingCustomerDigitalAccessInvitation delivery = transactions.enable(customerId, request);
        try {
            invitations.sendInvitation(delivery.recipientEmail(), delivery.rawVerificationToken());
        } catch (RuntimeException exception) {
            LOGGER.warn("Customer digital-access invitation delivery failed after activation commit.");
        }
        return delivery.status();
    }
}

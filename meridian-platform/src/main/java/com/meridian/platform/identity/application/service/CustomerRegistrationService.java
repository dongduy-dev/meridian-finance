package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import com.meridian.platform.identity.application.dto.CustomerRegistrationResponse;
import com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase;
import com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CustomerRegistrationService implements RegisterCustomerUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerRegistrationService.class);

    private final CustomerRegistrationTransactionService transactionService;
    private final EmailVerificationNotificationPort notificationPort;

    public CustomerRegistrationService(
            CustomerRegistrationTransactionService transactionService,
            EmailVerificationNotificationPort notificationPort
    ) {
        this.transactionService = Objects.requireNonNull(transactionService);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public CustomerRegistrationResponse register(CustomerRegistrationRequest request) {
        PendingEmailVerificationDelivery delivery = transactionService.register(request);
        try {
            notificationPort.sendVerificationEmail(delivery.recipientEmail(), delivery.rawToken());
        } catch (RuntimeException exception) {
            LOGGER.warn("Verification email delivery failed after registration commit.");
        }
        return CustomerRegistrationResponse.verificationRequired();
    }
}

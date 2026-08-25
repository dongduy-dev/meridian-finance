package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;
import com.meridian.platform.identity.application.dto.EmailVerificationRequest;
import com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class EmailVerificationService implements RequestEmailVerificationUseCase, ConfirmEmailVerificationUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTransactionService transactionService;
    private final EmailVerificationNotificationPort notificationPort;

    public EmailVerificationService(
            EmailVerificationTransactionService transactionService,
            EmailVerificationNotificationPort notificationPort
    ) {
        this.transactionService = Objects.requireNonNull(transactionService);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public void requestVerification(EmailVerificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        transactionService.issueForEmail(request.email()).ifPresent(delivery -> {
            try {
                notificationPort.sendVerificationEmail(delivery.recipientEmail(), delivery.rawToken());
            } catch (RuntimeException exception) {
                LOGGER.warn("Verification email delivery failed after token replacement commit.");
            }
        });
    }

    @Override
    public void confirmVerification(EmailVerificationConfirmationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        transactionService.confirm(request.token());
    }
}

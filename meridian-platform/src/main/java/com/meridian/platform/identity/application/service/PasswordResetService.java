package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.dto.PasswordResetRequest;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase;
import com.meridian.platform.identity.application.port.out.PasswordResetNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PasswordResetService implements RequestPasswordResetUseCase, ConfirmPasswordResetUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTransactionService transactionService;
    private final PasswordResetNotificationPort notificationPort;

    public PasswordResetService(
            PasswordResetTransactionService transactionService,
            PasswordResetNotificationPort notificationPort
    ) {
        this.transactionService = Objects.requireNonNull(transactionService);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public void requestReset(PasswordResetRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        transactionService.issueForEmail(request.email()).ifPresent(delivery -> {
            try {
                notificationPort.sendPasswordResetEmail(delivery.recipientEmail(), delivery.rawToken());
            } catch (RuntimeException exception) {
                LOGGER.warn("Password-reset email delivery failed after token replacement commit.");
            }
        });
    }

    @Override
    public void confirmReset(PasswordResetConfirmationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        transactionService.confirm(request.token(), request.newPassword());
    }
}

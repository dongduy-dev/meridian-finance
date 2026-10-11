package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapAdministratorServiceTest {
    @Test
    void deliveryFailureProvidesRecoveryDirectionWithoutRetainingSecretBearingCause() {
        var transactions = mock(ProvisionInternalUserTransactionService.class);
        var notification = mock(StaffPasswordSetupNotificationPort.class);
        var delivery = new PendingStaffSetupDelivery(new InternalUserDto(UUID.randomUUID(), "operator@meridian.test",
                "Operator", "ACTIVE", List.of("BACK_OFFICE_ADMIN")), "operator@meridian.test", "private-setup-token");
        when(transactions.bootstrapAdministrator("operator@meridian.test", "Operator")).thenReturn(delivery);
        doThrow(new IllegalStateException("Transport echoed private-setup-token")).when(notification)
                .sendSetupEmail(delivery.recipientEmail(), delivery.rawToken());
        var service = new BootstrapAdministratorService(transactions, notification);
        var failure = assertThrows(IllegalStateException.class, () -> service.bootstrap("operator@meridian.test", "Operator"));
        var ordered = inOrder(transactions, notification);
        ordered.verify(transactions).bootstrapAdministrator("operator@meridian.test", "Operator");
        ordered.verify(notification).sendSetupEmail(delivery.recipientEmail(), delivery.rawToken());
        assertTrue(failure.getMessage().contains("Disable bootstrap and use password recovery"));
        assertFalse(failure.getMessage().contains(delivery.rawToken()));
        assertNull(failure.getCause());
    }
}

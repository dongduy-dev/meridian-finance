package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerRegistrationServiceTest {

    @Test
    void requestsDeliveryAfterTransactionalRegistrationReturns() {
        CustomerRegistrationTransactionService transactionService = mock(CustomerRegistrationTransactionService.class);
        var notification = mock(com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort.class);
        CustomerRegistrationRequest request = request();
        when(transactionService.register(request)).thenReturn(
                new PendingEmailVerificationDelivery("customer@example.com", "raw-token")
        );

        var response = new CustomerRegistrationService(transactionService, notification).register(request);

        assertTrue(response.emailVerificationRequired());
        verify(transactionService).register(request);
        verify(notification).sendVerificationEmail("customer@example.com", "raw-token");
    }

    @Test
    void deliveryFailureDoesNotReplaceCommittedRegistrationSuccess() {
        CustomerRegistrationTransactionService transactionService = mock(CustomerRegistrationTransactionService.class);
        var notification = mock(com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort.class);
        CustomerRegistrationRequest request = request();
        when(transactionService.register(request)).thenReturn(
                new PendingEmailVerificationDelivery("customer@example.com", "raw-token")
        );
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notification)
                .sendVerificationEmail("customer@example.com", "raw-token");

        var response = new CustomerRegistrationService(transactionService, notification).register(request);

        assertTrue(response.emailVerificationRequired());
    }

    private CustomerRegistrationRequest request() {
        return new CustomerRegistrationRequest(
                "customer@example.com",
                "a-valid-password",
                "Customer Name"
        );
    }
}

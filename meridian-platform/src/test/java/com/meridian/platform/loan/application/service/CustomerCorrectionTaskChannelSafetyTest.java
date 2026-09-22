package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCorrectionTaskChannelSafetyTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock LoanCorrectionRepository corrections;
    @Mock LoanApplicationRepository applications;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUsers;
    @Mock BusinessAuditPublisher audits;
    private CustomerCorrectionTaskService service;

    @BeforeEach
    void setUp() {
        service = new CustomerCorrectionTaskService(
                corrections, applications, new CustomerCorrectionDocumentProof(documents), currentUsers, audits,
                Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC));
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                USER_ID, "customer@meridian.test", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("loan:correction:own")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(new LoanApplication(
                APPLICATION_ID, CUSTOMER_ID, UUID.randomUUID(), "UCL-ASSISTED-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                OriginationChannel.STAFF_ASSISTED, LoanApplicationStatus.RETURNED_FOR_REVISION,
                new BigDecimal("5000000.00"), 6, LocalDateTime.of(2026, 9, 17, 8, 0))));
    }

    @Test
    void customerCannotQueryStaffAssistedCorrectionTasks() {
        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.findOwnTasks(APPLICATION_ID));
        assertEquals("CUSTOMER_DIRECT_ACTION_NOT_ALLOWED", error.getErrorCode());
        verify(corrections, never()).findCustomerTasks(any(), any());
    }

    @Test
    void customerCannotCompleteStaffAssistedCorrectionTask() {
        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.complete(APPLICATION_ID, TASK_ID,
                        new CompleteCorrectionTaskRequest(UUID.randomUUID())));
        assertEquals("CUSTOMER_DIRECT_ACTION_NOT_ALLOWED", error.getErrorCode());
        verify(corrections, never()).findTaskByIdForUpdate(any());
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanDocumentWorkflowAdapterTest {
    @ParameterizedTest
    @EnumSource(LoanApplicationStatus.class)
    void allStatusAndChannelCodesPreserveFindVersusLockBehavior(LoanApplicationStatus status) {
        for (var channel : OriginationChannel.values()) {
            var repository = mock(LoanApplicationRepository.class);
            var app = new LoanApplication(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UCL-fixture",
                    ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED, channel, status,
                    new BigDecimal("5000000.00"), 6, LocalDateTime.of(2026, 10, 27, 12, 0));
            when(repository.findById(app.id())).thenReturn(Optional.of(app));
            when(repository.findByIdForUpdate(app.id())).thenReturn(Optional.of(app));
            var adapter = new LoanDocumentWorkflowAdapter(repository);
            var found = adapter.find(app.id());
            assertEquals(app.id(), found.loanApplicationId());
            assertEquals(app.customerId(), found.customerId());
            assertEquals(status.name(), found.status());
            assertEquals(channel.name(), found.originationChannel());
            verify(repository).findById(app.id());
            verify(repository, never()).acquireWorkflowLock(any());
            verify(repository, never()).findByIdForUpdate(any());
            clearInvocations(repository);
            assertEquals(found, adapter.lock(app.id()));
            var order = inOrder(repository);
            order.verify(repository).acquireWorkflowLock(app.id());
            order.verify(repository).findByIdForUpdate(app.id());
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void absentApplicationKeepsTheSameErrorAndLockOrder() {
        var repository = mock(LoanApplicationRepository.class);
        var adapter = new LoanDocumentWorkflowAdapter(repository);
        UUID id = UUID.randomUUID();
        var missing = assertThrows(EntityNotFoundException.class, () -> adapter.find(id));
        assertEquals("LOAN_APPLICATION_NOT_FOUND", missing.getErrorCode());
        assertEquals("Loan application was not found.", missing.getMessage());
        verify(repository, never()).acquireWorkflowLock(any());
        assertEquals(missing.getMessage(), assertThrows(EntityNotFoundException.class, () -> adapter.lock(id)).getMessage());
        var order = inOrder(repository);
        order.verify(repository).acquireWorkflowLock(id);
        order.verify(repository).findByIdForUpdate(id);
    }
}

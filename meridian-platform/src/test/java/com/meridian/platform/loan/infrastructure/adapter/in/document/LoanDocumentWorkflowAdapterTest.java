package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.loan.application.port.in.LoanDocumentWorkflowUseCase;
import com.meridian.platform.loan.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanDocumentWorkflowAdapterTest {
    @ParameterizedTest @EnumSource(LoanApplicationStatus.class)
    void mapsEveryStatusAndChannelWithoutChangingReadOrLockOperation(LoanApplicationStatus status) {
        for (var channel : OriginationChannel.values()) {
            var input = mock(LoanDocumentWorkflowUseCase.class);
            var facts = new LoanDocumentWorkflowUseCase.LoanDocumentWorkflowSnapshot(UUID.randomUUID(), UUID.randomUUID(), channel, status);
            when(input.find(facts.loanApplicationId())).thenReturn(facts);
            when(input.lock(facts.loanApplicationId())).thenReturn(facts);
            var adapter = new LoanDocumentWorkflowAdapter(input);
            var read = adapter.find(facts.loanApplicationId());
            assertEquals(facts.loanApplicationId(), read.loanApplicationId());
            assertEquals(facts.customerId(), read.customerId());
            assertEquals(channel.name(), read.originationChannel());
            assertEquals(status.name(), read.status());
            verify(input).find(facts.loanApplicationId());
            verify(input, never()).lock(any());
            assertEquals(read, adapter.lock(facts.loanApplicationId()));
            verify(input).lock(facts.loanApplicationId());
            verifyNoMoreInteractions(input);
        }
    }

    @Test void propagatesExactProviderFailuresFromBothOperations() {
        UUID id = UUID.randomUUID();
        var failure = new IllegalStateException("workflow failure");
        var input = mock(LoanDocumentWorkflowUseCase.class, invocation -> { throw failure; });
        var adapter = new LoanDocumentWorkflowAdapter(input);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.find(id)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.lock(id)));
    }
}

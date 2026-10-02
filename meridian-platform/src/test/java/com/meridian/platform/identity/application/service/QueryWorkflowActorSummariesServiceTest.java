package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.in.WorkflowActorSummary;
import com.meridian.platform.identity.application.port.out.WorkflowActorSummaryRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryWorkflowActorSummariesServiceTest {
    @Test void batchesOnlyRecordedIdsAndOmitsUnresolvedUsers() {
        var repository = mock(WorkflowActorSummaryRepository.class);
        var service = new QueryWorkflowActorSummariesService(repository);
        UUID customerUserId = UUID.randomUUID(), missing = UUID.randomUUID(), customerId = UUID.randomUUID();
        var ids = Set.of(customerUserId, missing);
        when(repository.findByUserIds(ids)).thenReturn(Map.of(customerUserId,
                new WorkflowActorSummary(customerUserId, "CUSTOMER", customerId, null)));
        var result = service.findByUserIds(ids);
        assertEquals(customerId, result.get(customerUserId).customerId());
        assertNull(result.get(customerUserId).staff());
        assertFalse(result.containsKey(missing));
        assertThrows(UnsupportedOperationException.class, result::clear);
        verify(repository).findByUserIds(ids);
        verifyNoMoreInteractions(repository);
    }

    @Test void emptyBatchDoesNotQueryAndInvalidInputIsRejected() {
        var repository = mock(WorkflowActorSummaryRepository.class);
        var service = new QueryWorkflowActorSummariesService(repository);
        assertTrue(service.findByUserIds(Set.of()).isEmpty());
        assertThrows(NullPointerException.class, () -> service.findByUserIds(null));
        Set<UUID> invalid = new HashSet<>(); invalid.add(null);
        assertThrows(IllegalArgumentException.class, () -> service.findByUserIds(invalid));
        verifyNoInteractions(repository);
    }
}

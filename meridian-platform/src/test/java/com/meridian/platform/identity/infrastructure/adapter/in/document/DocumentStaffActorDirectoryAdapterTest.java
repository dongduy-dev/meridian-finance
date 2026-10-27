package com.meridian.platform.identity.infrastructure.adapter.in.document;

import com.meridian.platform.identity.application.port.in.QueryStaffActorSummariesUseCase;
import com.meridian.platform.identity.application.port.in.StaffActorSummary;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentStaffActorDirectoryAdapterTest {
    private final QueryStaffActorSummariesUseCase input = mock(QueryStaffActorSummariesUseCase.class);
    private final DocumentStaffActorDirectoryAdapter adapter = new DocumentStaffActorDirectoryAdapter(input);

    @Test void forwardsFiniteIdsAndCopiesCompleteMissingAndIncompleteSummariesImmutably() {
        UUID complete = UUID.randomUUID(), incomplete = UUID.randomUUID(), missing = UUID.randomUUID();
        var ids = Set.of(complete, incomplete, missing);
        var source = new HashMap<UUID, StaffActorSummary>();
        source.put(complete, new StaffActorSummary(complete, "Fictional reviewer", "reviewer@meridian.test"));
        source.put(incomplete, new StaffActorSummary(incomplete, "", ""));
        when(input.findByUserIds(ids)).thenReturn(source);
        var result = adapter.findByUserIds(ids);
        assertEquals(ids.size()-1, result.size());
        assertEquals("Fictional reviewer", result.get(complete).displayName());
        assertEquals("reviewer@meridian.test", result.get(complete).email());
        assertEquals(complete, result.get(complete).userId());
        assertEquals("", result.get(incomplete).displayName());
        assertFalse(result.containsKey(missing));
        assertThrows(UnsupportedOperationException.class, result::clear);
        source.clear(); assertEquals(2, result.size());
        verify(input).findByUserIds(ids); verifyNoMoreInteractions(input);
    }
    @Test void preservesEmptyAndInvalidInputProviderSemanticsAndFailureIdentity() {
        when(input.findByUserIds(Set.of())).thenReturn(Map.of());
        assertEquals(Map.of(), adapter.findByUserIds(Set.of()));
        var nullFailure = new NullPointerException("userIds must not be null");
        when(input.findByUserIds(null)).thenThrow(nullFailure);
        assertSame(nullFailure, assertThrows(NullPointerException.class, () -> adapter.findByUserIds(null)));
        UUID id = UUID.randomUUID(); var failure = new IllegalStateException("identity read failure");
        when(input.findByUserIds(Set.of(id))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.findByUserIds(Set.of(id))));
    }
}

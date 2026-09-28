package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryPartnerEmployeeServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CURRENT_BATCH_ID = UUID.randomUUID();
    private final PartnerEmployeeRepository employees = mock(PartnerEmployeeRepository.class);
    private final PartnerEmployeeImportBatchRepository batches = mock(PartnerEmployeeImportBatchRepository.class);
    private final QueryPartnerEmployeeService service = new QueryPartnerEmployeeService(
            employees, batches, new PartnerEmployeeMapper(),
            Clock.fixed(Instant.parse("2026-09-29T23:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void currentSnapshotUsesServerMonthAndOnlySelectedBatchRows() {
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(COMPANY_ID, "2026-09"))
                .thenReturn(Optional.of(new PartnerEmployeeImportBatch(
                        CURRENT_BATCH_ID, COMPANY_ID, "2026-09", PartnerEmployeeImportBatchStatus.COMPLETED, 1, 0
                )));
        when(employees.findByPartnerCompanyIdAndImportBatchId(COMPANY_ID, CURRENT_BATCH_ID))
                .thenReturn(List.of(employee(CURRENT_BATCH_ID, "EMP-001")));

        var snapshot = service.getCurrentPartnerEmployeeSnapshot(COMPANY_ID);

        assertEquals(COMPANY_ID, snapshot.partnerCompanyId());
        assertEquals("2026-09", snapshot.effectiveMonth());
        assertEquals(CURRENT_BATCH_ID, snapshot.authoritativeBatchId());
        assertEquals(List.of("EMP-001"), snapshot.employees().stream().map(e -> e.employeeCode()).toList());
        verify(employees, never()).findByPartnerCompanyId(COMPANY_ID);
    }

    @Test
    void noCompletedCurrentBatchDoesNotFallBackToHistoricalRows() {
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(COMPANY_ID, "2026-09"))
                .thenReturn(Optional.empty());

        var snapshot = service.getCurrentPartnerEmployeeSnapshot(COMPANY_ID);

        assertEquals("2026-09", snapshot.effectiveMonth());
        assertNull(snapshot.authoritativeBatchId());
        assertEquals(List.of(), snapshot.employees());
        verify(employees, never()).findByPartnerCompanyId(COMPANY_ID);
    }

    @Test
    void allCompanyReadRetainsExistingBehavior() {
        UUID olderBatchId = UUID.randomUUID();
        when(employees.findByPartnerCompanyId(COMPANY_ID)).thenReturn(List.of(
                employee(olderBatchId, "EMP-001"), employee(CURRENT_BATCH_ID, "EMP-001")
        ));

        var rows = service.getPartnerEmployeesByCompanyId(COMPANY_ID, false);

        assertEquals(2, rows.size());
        assertEquals(olderBatchId, rows.getFirst().importBatchId());
        assertEquals(CURRENT_BATCH_ID, rows.getLast().importBatchId());
    }

    private static PartnerEmployee employee(UUID batchId, String code) {
        return new PartnerEmployee(UUID.randomUUID(), COMPANY_ID, batchId, code, "ID-1",
                BigDecimal.TEN, BigDecimal.ONE, PartnerEmployeeStatus.ACTIVE, true);
    }
}

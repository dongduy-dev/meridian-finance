package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.mapper.PartnerEmployeeImportBatchMapper;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.*;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryPartnerEmployeeImportBatchServiceTest {
    private final UUID companyId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final PartnerCompanyRepository companies = mock(PartnerCompanyRepository.class);
    private final PartnerEmployeeImportBatchRepository batches = mock(PartnerEmployeeImportBatchRepository.class);
    private final PartnerEmployeeRepository employees = mock(PartnerEmployeeRepository.class);
    private final QueryPartnerEmployeeImportBatchService service = new QueryPartnerEmployeeImportBatchService(
            companies, batches, new PartnerEmployeeImportBatchMapper(), employees, new PartnerEmployeeMapper());

    @Test
    void completedBatchReturnsOnlyItsMaskedRosterWithoutWritesOrAuthoritySelection() {
        companyExists();
        when(batches.findByPartnerCompanyIdAndId(companyId, batchId)).thenReturn(Optional.of(
                new PartnerEmployeeImportBatch(batchId, companyId, "2026-08",
                        PartnerEmployeeImportBatchStatus.COMPLETED, 1, 0, UUID.randomUUID(), "PRIVATE", List.of())));
        var employee = new PartnerEmployee(UUID.randomUUID(), companyId, batchId, "EMP-HISTORY",
                "FICTIONAL-PRIVATE-1234", BigDecimal.TEN, BigDecimal.ONE, PartnerEmployeeStatus.ACTIVE, true);
        when(employees.findByPartnerCompanyIdAndImportBatchId(companyId, batchId)).thenReturn(List.of(employee));

        var detail = service.getImportBatch(companyId, batchId);

        assertEquals(batchId, detail.id());
        assertEquals("2026-08", detail.effectiveMonth());
        assertEquals("****1234", detail.employees().getFirst().maskedIdentityReference());
        var json = tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(detail);
        for (String excluded : List.of("identityReference", "requestId", "requestFingerprint", "audit")) {
            assertFalse(json.toString().contains("\"" + excluded + "\""));
        }
        assertFalse(json.toString().contains(employee.identityReference()));
        verify(companies).findById(companyId);
        verify(batches).findByPartnerCompanyIdAndId(companyId, batchId);
        verify(employees).findByPartnerCompanyIdAndImportBatchId(companyId, batchId);
        verifyNoMoreInteractions(companies, batches, employees);
    }

    @Test
    void failedAndNonterminalBatchesNeverReadOrExposeImportedRows() {
        companyExists();
        for (var status : List.of(PartnerEmployeeImportBatchStatus.FAILED,
                PartnerEmployeeImportBatchStatus.PENDING, PartnerEmployeeImportBatchStatus.PROCESSING)) {
            var rejection = new PartnerEmployeeImportRejection(2, "INVALID_EMPLOYEE_CODE", "Employee code is required.");
            when(batches.findByPartnerCompanyIdAndId(companyId, batchId)).thenReturn(Optional.of(
                    new PartnerEmployeeImportBatch(batchId, companyId, "2026-08", status, 1, 1,
                            null, null, List.of(rejection))));
            var detail = service.getImportBatch(companyId, batchId);
            assertEquals(status.name(), detail.status());
            assertEquals(1, detail.validRowCount());
            assertTrue(detail.employees().isEmpty());
            assertEquals("INVALID_EMPLOYEE_CODE", detail.rejections().getFirst().errorCode());
        }
        verifyNoInteractions(employees);
    }

    @Test
    void unknownCompanyDoesNotLookUpBatchOrRoster() {
        assertThrows(EntityNotFoundException.class, () -> service.getImportBatch(companyId, batchId));
        verifyNoInteractions(batches, employees);
    }

    @Test
    void unknownOrForeignBatchDoesNotReturnAnEmptySuccessfulDetail() {
        companyExists();
        assertThrows(EntityNotFoundException.class, () -> service.getImportBatch(companyId, batchId));
        verify(batches).findByPartnerCompanyIdAndId(companyId, batchId);
        verifyNoInteractions(employees);
    }

    private void companyExists() {
        when(companies.findById(companyId)).thenReturn(Optional.of(new PartnerCompany(
                companyId, "FICTIONAL", "Fictional Partner", PartnerCompanyStatus.ACTIVE, BigDecimal.TEN)));
    }
}

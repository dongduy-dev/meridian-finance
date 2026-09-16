package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportPartnerEmployeesServiceTest {

    private final PartnerCompanyRepository companies = mock(PartnerCompanyRepository.class);
    private final PartnerEmployeeImportBatchRepository batches = mock(PartnerEmployeeImportBatchRepository.class);
    private final PartnerEmployeeRepository employees = mock(PartnerEmployeeRepository.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID companyId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID requestId = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private final AtomicReference<PartnerEmployeeImportBatch> stored = new AtomicReference<>();
    private ImportPartnerEmployeesService service;

    @BeforeEach
    void setUp() {
        when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.of(new PartnerCompany(
                companyId, "ACME", "Acme", PartnerCompanyStatus.SUSPENDED, new BigDecimal("20000000")
        )));
        when(batches.findByRequestId(requestId)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(batches.save(any())).thenAnswer(invocation -> {
            PartnerEmployeeImportBatch batch = invocation.getArgument(0);
            stored.set(batch);
            return batch;
        });
        when(employees.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:manage")
        ));
        service = new ImportPartnerEmployeesService(
                companies, batches, employees, users, audits,
                Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void completesMixedBatchAndPersistsOnlyValidRows() {
        var result = service.importEmployees(companyId, request(List.of(
                row("EMP-1", "ID-1", "ACTIVE", true),
                row("EMP-2", "ID-2", "FUTURE", true),
                row("EMP-3", "ID-3", "ACTIVE", true)
        )));

        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.validRowCount());
        assertEquals(1, result.invalidRowCount());
        assertEquals("INVALID_EMPLOYMENT_STATUS", result.rejections().getFirst().errorCode());
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<com.meridian.platform.partner.domain.model.PartnerEmployee>> savedEmployees =
                ArgumentCaptor.forClass((Class) List.class);
        verify(employees).saveAll(savedEmployees.capture());
        assertEquals(List.of("EMP-1", "EMP-3"), savedEmployees.getValue().stream().map(e -> e.employeeCode()).toList());
        var audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(BusinessAuditAction.PARTNER_EMPLOYEE_IMPORT_COMPLETED, audit.getValue().entries().getFirst().action());
        assertEquals(requestId, audit.getValue().operationContext().operationId());
        String safeAudit = audit.getValue().entries().getFirst().payload().values().toString();
        org.junit.jupiter.api.Assertions.assertFalse(safeAudit.contains("EMP-1"));
        org.junit.jupiter.api.Assertions.assertFalse(safeAudit.contains("ID-1"));
        org.junit.jupiter.api.Assertions.assertFalse(safeAudit.contains("10000000"));
    }

    @Test
    void completesFullyValidBatchWithExactCounts() {
        var result = service.importEmployees(companyId, request(List.of(
                row("EMP-1", "ID-1", "ACTIVE", true),
                row("EMP-2", "ID-2", "SUSPENDED", false)
        )));

        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.validRowCount());
        assertEquals(0, result.invalidRowCount());
        assertEquals(List.of(), result.rejections());
    }

    @Test
    void missingCompanyCreatesNoBatchOrEmployees() {
        when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.empty());

        EntityNotFoundException error = assertThrows(EntityNotFoundException.class, () ->
                service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true)))));

        assertEquals("PARTNER_COMPANY_NOT_FOUND", error.getErrorCode());
        verify(batches, never()).save(any());
        verify(employees, never()).saveAll(any());
        verify(audits, never()).publish(any());
    }

    @Test
    void excludesEveryRowForDuplicatedEmployeeCode() {
        var result = service.importEmployees(companyId, request(List.of(
                row("EMP-1", "ID-1", "ACTIVE", true),
                row("EMP-1", "ID-2", "ACTIVE", true)
        )));
        assertEquals(0, result.validRowCount());
        assertEquals(2, result.invalidRowCount());
        assertEquals(List.of("DUPLICATE_EMPLOYEE_CODE", "DUPLICATE_EMPLOYEE_CODE"),
                result.rejections().stream().map(r -> r.errorCode()).toList());
    }

    @Test
    void exactReplayReturnsOriginalOutcomeWithoutDuplicateEffects() {
        ImportPartnerEmployeesRequest request = request(List.of(row("EMP-1", "ID-1", "ACTIVE", true)));
        var first = service.importEmployees(companyId, request);
        var replay = service.importEmployees(companyId, request);
        assertEquals(first, replay);
        verify(batches, times(2)).acquireRequestLock(requestId);
        verify(batches, times(1)).save(any());
        verify(employees, times(1)).saveAll(any());
        verify(audits, times(1)).publish(any());
    }

    @Test
    void conflictingReplayFailsBeforeBusinessMutation() {
        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class, () ->
                service.importEmployees(companyId, request(List.of(row("EMP-2", "ID-2", "ACTIVE", true)))));
        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verify(batches, times(1)).save(any());
    }

    @Test
    void acceptsExactFourDigitEffectiveMonth() {
        var result = service.importEmployees(companyId, new ImportPartnerEmployeesRequest(
                requestId, "2026-01", List.of(row("EMP-1", "ID-1", "ACTIVE", true))
        ));

        assertEquals("2026-01", result.effectiveMonth());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "+10000-01", "-0001-01"})
    void rejectsInvalidOrNonFourDigitEffectiveMonthBeforeLocking(String effectiveMonth) {
        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class, () ->
                service.importEmployees(companyId, new ImportPartnerEmployeesRequest(
                        requestId, effectiveMonth, List.of(row("EMP-1", "ID-1", "ACTIVE", true))
                )));
        assertEquals("INVALID_EFFECTIVE_MONTH", error.getErrorCode());
        verify(batches, never()).acquireRequestLock(any());
    }

    private ImportPartnerEmployeesRequest request(List<PartnerEmployeeImportRowRequest> rows) {
        return new ImportPartnerEmployeesRequest(requestId, "2026-09", rows);
    }

    private PartnerEmployeeImportRowRequest row(String code, String identity, String status, Boolean active) {
        return new PartnerEmployeeImportRowRequest(
                code, identity, new BigDecimal("10000000.00"), new BigDecimal("4000000.00"), status, active
        );
    }
}

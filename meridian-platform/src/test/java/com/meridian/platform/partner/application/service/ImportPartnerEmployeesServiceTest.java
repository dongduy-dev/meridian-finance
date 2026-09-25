package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportPartnerEmployeesServiceTest {

    private final PartnerCompanyRepository companies = mock(PartnerCompanyRepository.class);
    private final PartnerEmployeeImportBatchRepository batches = mock(PartnerEmployeeImportBatchRepository.class);
    private final PartnerEmployeeRepository employees = mock(PartnerEmployeeRepository.class);
    private final CustomerPartnerEmployeeLinkRepository links = mock(CustomerPartnerEmployeeLinkRepository.class);
    private final PartnerEligibilityReviewRepository reviews = mock(PartnerEligibilityReviewRepository.class);
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
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(companyId, "2026-09"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(employees.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.findVerifiedLinkIdsByPartnerCompanyId(companyId)).thenReturn(List.of());
        when(links.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviews.findPendingByCustomerIdAndPartnerCompanyId(any(), eq(companyId)))
                .thenReturn(Optional.empty());
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:manage")
        ));
        service = new ImportPartnerEmployeesService(
                companies, batches, employees, links, reviews, users, audits,
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
    void completesFullyValidBatchForSuspendedCompanyWithExactCounts() {
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
    void authoritativeCurrentMonthImportRefreshesSameVerifiedLinkToExactActiveEmployee() {
        CustomerPartnerEmployeeLink existing = existingLink();
        UUID replacementEmployeeId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        discoverVerifiedLink(existing);
        when(employees.findByVerificationEvidence(
                eq(companyId), any(UUID.class), eq("ID-1"), eq("EMP-1")
        )).thenAnswer(invocation -> List.of(new PartnerEmployee(
                replacementEmployeeId,
                companyId,
                invocation.getArgument(1),
                "EMP-1",
                "ID-1",
                new BigDecimal("12000000.00"),
                new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE,
                true
        )));

        var result = service.importEmployees(
                companyId,
                request(List.of(row("EMP-1", "ID-1", "ACTIVE", true)))
        );

        ArgumentCaptor<CustomerPartnerEmployeeLink> refreshed =
                ArgumentCaptor.forClass(CustomerPartnerEmployeeLink.class);
        verify(links).save(refreshed.capture());
        assertEquals(existing.id(), refreshed.getValue().id());
        assertEquals(replacementEmployeeId, refreshed.getValue().partnerEmployeeId());
        assertEquals(result.importBatchId(), refreshed.getValue().sourceImportBatchId());
        assertEquals(existing.lastVerifiedAt(), refreshed.getValue().lastVerifiedAt());
        assertEquals(LocalDateTime.of(2026, 9, 16, 8, 0), refreshed.getValue().lastRefreshedAt());
        assertEquals(EmployeeVerificationOutcome.MATCHED_ACTIVE, refreshed.getValue().verificationOutcome());
        assertEquals(CustomerPartnerEmployeeLinkStatus.VERIFIED, refreshed.getValue().linkStatus());
    }

    @Test
    void nonCurrentMonthImportDoesNotReconcileLinks() {
        service.importEmployees(companyId, new ImportPartnerEmployeesRequest(
                requestId, "2026-08", List.of(row("EMP-1", "ID-1", "ACTIVE", true))
        ));

        verify(links, never()).findVerifiedLinkIdsByPartnerCompanyId(any());
        verify(links, never()).save(any());
    }

    @Test
    void sameMonthNonAuthoritativeImportDoesNotReconcileLinks() {
        PartnerEmployeeImportBatch otherAuthoritativeBatch = new PartnerEmployeeImportBatch(
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                companyId,
                "2026-09",
                PartnerEmployeeImportBatchStatus.COMPLETED,
                1,
                0,
                UUID.randomUUID(),
                "other-fingerprint",
                List.of()
        );
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(companyId, "2026-09"))
                .thenReturn(Optional.of(otherAuthoritativeBatch));

        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));

        verify(links, never()).findVerifiedLinkIdsByPartnerCompanyId(any());
        verify(links, never()).save(any());
    }

    @Test
    void missingCurrentEvidenceLeavesVerifiedLinkUntouched() {
        discoverVerifiedLink(existingLink());
        when(employees.findByVerificationEvidence(
                eq(companyId), any(UUID.class), eq("ID-1"), eq("EMP-1")
        )).thenReturn(List.of());

        service.importEmployees(companyId, request(List.of(row("EMP-2", "ID-2", "ACTIVE", true))));

        verify(links, never()).save(any());
    }

    @Test
    void inactiveCurrentEvidenceLeavesVerifiedLinkUntouched() {
        discoverVerifiedLink(existingLink());
        when(employees.findByVerificationEvidence(
                eq(companyId), any(UUID.class), eq("ID-1"), eq("EMP-1")
        )).thenAnswer(invocation -> List.of(new PartnerEmployee(
                UUID.randomUUID(), companyId, invocation.getArgument(1), "EMP-1", "ID-1",
                new BigDecimal("12000000.00"), new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE, false
        )));

        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", false))));

        verify(links, never()).save(any());
    }

    @Test
    void ambiguousCurrentEvidenceLeavesVerifiedLinkUntouched() {
        discoverVerifiedLink(existingLink());
        when(employees.findByVerificationEvidence(
                eq(companyId), any(UUID.class), eq("ID-1"), eq("EMP-1")
        )).thenAnswer(invocation -> List.of(
                activeEmployee(UUID.randomUUID(), invocation.getArgument(1)),
                activeEmployee(UUID.randomUUID(), invocation.getArgument(1))
        ));

        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));

        verify(links, never()).save(any());
    }

    @Test
    void pendingEligibilityReviewLeavesRelationshipUnderManualAuthority() {
        CustomerPartnerEmployeeLink existing = existingLink();
        discoverVerifiedLink(existing);
        when(reviews.findPendingByCustomerIdAndPartnerCompanyId(existing.customerId(), companyId))
                .thenReturn(Optional.of(mock(PartnerEligibilityReview.class)));

        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));

        verify(employees, never()).findByVerificationEvidence(any(), any(), any(), any());
        verify(links, never()).save(any());
    }

    @Test
    void lockedRecheckSkipsRelationshipDisplacedAfterCandidateDiscovery() {
        CustomerPartnerEmployeeLink candidate = existingLink();
        when(links.findVerifiedLinkIdsByPartnerCompanyId(companyId)).thenReturn(List.of(candidate.id()));
        when(links.findVerifiedByIdAndPartnerCompanyIdForUpdate(candidate.id(), companyId))
                .thenReturn(Optional.empty());

        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));

        verify(employees, never()).findByVerificationEvidence(any(), any(), any(), any());
        verify(links, never()).save(any());
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
        prepareActiveRefresh();
        var first = service.importEmployees(companyId, request);
        var replay = service.importEmployees(companyId, request);
        assertEquals(first, replay);
        verify(batches, times(2)).acquireRequestLock(requestId);
        verify(batches, times(1)).save(any());
        verify(employees, times(1)).saveAll(any());
        verify(links, times(1)).save(any());
        verify(audits, times(1)).publish(any());
    }

    @Test
    void conflictingReplayFailsBeforeBusinessMutation() {
        prepareActiveRefresh();
        service.importEmployees(companyId, request(List.of(row("EMP-1", "ID-1", "ACTIVE", true))));
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class, () ->
                service.importEmployees(companyId, request(List.of(row("EMP-2", "ID-2", "ACTIVE", true)))));
        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verify(batches, times(1)).save(any());
        verify(links, times(1)).save(any());
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

    private CustomerPartnerEmployeeLink existingLink() {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 15, 7, 30);
        return new CustomerPartnerEmployeeLink(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                companyId,
                UUID.fromString("77777777-7777-4777-8777-777777777777"),
                UUID.fromString("88888888-8888-4888-8888-888888888888"),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                "ID-1",
                "EMP-1",
                verifiedAt,
                verifiedAt
        );
    }

    private void discoverVerifiedLink(CustomerPartnerEmployeeLink link) {
        when(links.findVerifiedLinkIdsByPartnerCompanyId(companyId)).thenReturn(List.of(link.id()));
        when(links.findVerifiedByIdAndPartnerCompanyIdForUpdate(link.id(), companyId))
                .thenReturn(Optional.of(link));
    }

    private void prepareActiveRefresh() {
        CustomerPartnerEmployeeLink existing = existingLink();
        discoverVerifiedLink(existing);
        when(employees.findByVerificationEvidence(
                eq(companyId), any(UUID.class), eq("ID-1"), eq("EMP-1")
        )).thenAnswer(invocation -> List.of(new PartnerEmployee(
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                companyId,
                invocation.getArgument(1),
                "EMP-1",
                "ID-1",
                new BigDecimal("12000000.00"),
                new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE,
                true
        )));
    }

    private PartnerEmployee activeEmployee(UUID employeeId, UUID importBatchId) {
        return new PartnerEmployee(
                employeeId,
                companyId,
                importBatchId,
                "EMP-1",
                "ID-1",
                new BigDecimal("12000000.00"),
                new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE,
                true
        );
    }
}

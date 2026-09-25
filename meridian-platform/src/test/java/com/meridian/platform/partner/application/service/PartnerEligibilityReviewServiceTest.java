package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewDecision;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerEligibilityReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T08:00:00Z"), ZoneOffset.UTC);
    private static final UUID REVIEW_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID BATCH_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID EMPLOYEE_ID = UUID.fromString("50000000-0000-4000-8000-000000000005");
    private static final UUID ACTOR_ID = UUID.fromString("60000000-0000-4000-8000-000000000006");

    @Mock PartnerEligibilityReviewRepository reviews;
    @Mock PartnerCompanyRepository companies;
    @Mock PartnerEmployeeImportBatchRepository batches;
    @Mock PartnerEmployeeRepository employees;
    @Mock CustomerPartnerEmployeeLinkRepository links;
    @Mock CustomerIdentityEvidencePort identityEvidence;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock BusinessAuditPublisher auditPublisher;

    private PartnerEligibilityReviewService service;

    @BeforeEach
    void setUp() {
        service = new PartnerEligibilityReviewService(
                reviews, companies, batches, employees, links, identityEvidence,
                currentUserProvider, auditPublisher, CLOCK
        );
        when(currentUserProvider.currentUser()).thenReturn(actor());
        when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(pending()));
        when(reviews.findLockIdentityById(REVIEW_ID))
                .thenReturn(Optional.of(new PartnerEligibilityReviewRepository.LockIdentity(CUSTOMER_ID, COMPANY_ID)));
        when(reviews.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(pending()));
        when(companies.findByIdForUpdate(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(COMPANY_ID, "2026-09"))
                .thenReturn(Optional.of(batch()));
        when(identityEvidence.findIdentityEvidenceByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(identity()));
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvalCreatesManualReviewApprovedLinkAndPiiSafeActorBoundAudit() {
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee("IDENTITY-SECRET")));
        when(employees.findByIdentityEvidence(COMPANY_ID, BATCH_ID, "IDENTITY-SECRET"))
                .thenReturn(List.of(employee("IDENTITY-SECRET")));

        var result = service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.APPROVE,
                EMPLOYEE_ID,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
        ));

        assertEquals("APPROVED", result.status());
        assertEquals("MANUAL_REVIEW_APPROVED", result.decisionOutcome());
        var link = ArgumentCaptor.forClass(
                com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink.class
        );
        verify(links).save(link.capture());
        assertEquals(EmployeeVerificationOutcome.MANUAL_REVIEW_APPROVED, link.getValue().verificationOutcome());
        assertEquals(EMPLOYEE_ID, link.getValue().partnerEmployeeId());

        ArgumentCaptor<BusinessAuditEvent> audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(auditPublisher).publish(audit.capture());
        assertEquals(ACTOR_ID, audit.getValue().operationContext().actorUserId());
        String payload = audit.getValue().entries().getFirst().payload().values().toString();
        assertFalse(payload.contains("IDENTITY-SECRET"));
        assertFalse(payload.contains("EMP-001"));
        assertTrue(payload.contains(REVIEW_ID.toString()));
    }

    @Test
    void rejectionIsTerminalWithoutCreatingVerifiedLink() {
        var result = service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.REJECT,
                null,
                PartnerEligibilityReviewReason.NO_ELIGIBLE_CURRENT_EMPLOYEE
        ));

        assertEquals("REJECTED", result.status());
        assertEquals("MANUAL_REVIEW_REJECTED", result.decisionOutcome());
        verify(links, never()).save(any());
        verify(links, never()).saveAndFlush(any());
        verify(auditPublisher).publish(any());
    }

    @Test
    void approvalForDifferentEmployerDisablesOldCurrentAndCreatesFreshRelationship() {
        UUID oldCompanyId = UUID.fromString("30000000-0000-4000-8000-000000000099");
        CustomerPartnerEmployeeLink oldCurrent = new CustomerPartnerEmployeeLink(
                UUID.fromString("70000000-0000-4000-8000-000000000007"),
                CUSTOMER_ID, oldCompanyId, UUID.randomUUID(), UUID.randomUUID(),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                "IDENTITY-SECRET", "OLD-EMP-001",
                LocalDateTime.now(CLOCK).minusMonths(1), LocalDateTime.now(CLOCK).minusMonths(1)
        );
        when(links.findCurrentVerifiedByCustomerIdForUpdate(CUSTOMER_ID))
                .thenReturn(Optional.of(oldCurrent));
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee("IDENTITY-SECRET")));

        service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.APPROVE,
                EMPLOYEE_ID,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
        ));

        ArgumentCaptor<CustomerPartnerEmployeeLink> disabled =
                ArgumentCaptor.forClass(CustomerPartnerEmployeeLink.class);
        ArgumentCaptor<CustomerPartnerEmployeeLink> created =
                ArgumentCaptor.forClass(CustomerPartnerEmployeeLink.class);
        verify(links).saveAndFlush(disabled.capture());
        verify(links).save(created.capture());
        assertEquals(CustomerPartnerEmployeeLinkStatus.DISABLED, disabled.getValue().linkStatus());
        assertEquals(oldCurrent.id(), disabled.getValue().id());
        assertEquals(CustomerPartnerEmployeeLinkStatus.VERIFIED, created.getValue().linkStatus());
        assertEquals(COMPANY_ID, created.getValue().partnerCompanyId());
        assertNotEquals(oldCurrent.id(), created.getValue().id());
    }

    @Test
    void approvalForSameEmployerUpdatesCurrentRelationshipWithoutCreatingFreshHistory() {
        CustomerPartnerEmployeeLink current = new CustomerPartnerEmployeeLink(
                UUID.fromString("70000000-0000-4000-8000-000000000007"),
                CUSTOMER_ID, COMPANY_ID, UUID.randomUUID(), UUID.randomUUID(),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                "IDENTITY-SECRET", "OLD-EMP-001",
                LocalDateTime.now(CLOCK).minusMonths(1), LocalDateTime.now(CLOCK).minusMonths(1)
        );
        when(links.findCurrentVerifiedByCustomerIdForUpdate(CUSTOMER_ID))
                .thenReturn(Optional.of(current));
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee("IDENTITY-SECRET")));

        service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.APPROVE,
                EMPLOYEE_ID,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
        ));

        ArgumentCaptor<CustomerPartnerEmployeeLink> saved =
                ArgumentCaptor.forClass(CustomerPartnerEmployeeLink.class);
        verify(links).save(saved.capture());
        verify(links, never()).saveAndFlush(any());
        assertEquals(current.id(), saved.getValue().id());
        assertEquals(EmployeeVerificationOutcome.MANUAL_REVIEW_APPROVED,
                saved.getValue().verificationOutcome());
    }

    @Test
    void approvalRejectsEmployeeOutsideCurrentCustomerIdentityEvidence() {
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee("OTHER-IDENTITY")));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.APPROVE,
                        EMPLOYEE_ID,
                        PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                ))
        );

        assertEquals("PARTNER_ELIGIBILITY_EMPLOYEE_INVALID", exception.getErrorCode());
        verify(links, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void approvalRejectsInactiveEmployee() {
        PartnerEmployee inactive = new PartnerEmployee(
                EMPLOYEE_ID, COMPANY_ID, BATCH_ID, "EMP-001", "IDENTITY-SECRET",
                new BigDecimal("12000000.00"), new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.INACTIVE, false
        );
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(inactive));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.APPROVE,
                        EMPLOYEE_ID,
                        PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                ))
        );

        assertEquals("PARTNER_EMPLOYEE_INACTIVE", exception.getErrorCode());
        verify(links, never()).save(any());
    }

    @Test
    void approvalRejectsEmployeeFromAnotherPartner() {
        PartnerEmployee foreignEmployee = new PartnerEmployee(
                EMPLOYEE_ID, UUID.randomUUID(), BATCH_ID, "EMP-001", "IDENTITY-SECRET",
                new BigDecimal("12000000.00"), new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE, true
        );
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(foreignEmployee));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.APPROVE,
                        EMPLOYEE_ID,
                        PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                ))
        );

        assertEquals("PARTNER_ELIGIBILITY_EMPLOYEE_INVALID", exception.getErrorCode());
        verify(links, never()).save(any());
    }

    @Test
    void replacedAuthoritativeBatchMakesReviewStale() {
        PartnerEmployeeImportBatch replacement = new PartnerEmployeeImportBatch(
                UUID.randomUUID(), COMPANY_ID, "2026-09",
                PartnerEmployeeImportBatchStatus.COMPLETED, 1, 0
        );
        when(batches.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(COMPANY_ID, "2026-09"))
                .thenReturn(Optional.of(replacement));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.APPROVE,
                        EMPLOYEE_ID,
                        PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
                ))
        );

        assertEquals("PARTNER_ELIGIBILITY_REVIEW_STALE", exception.getErrorCode());
        verify(links, never()).save(any());
    }

    @Test
    void priorMonthReviewCannotBeDecided() {
        PartnerEligibilityReview stale = new PartnerEligibilityReview(
                REVIEW_ID, CUSTOMER_ID, COMPANY_ID, "2026-08", BATCH_ID,
                EmployeeVerificationOutcome.NOT_FOUND, "EMP-001",
                PartnerEligibilityReviewStatus.PENDING, null, null, null, null, null, null,
                LocalDateTime.now(CLOCK).minusMonths(1), LocalDateTime.now(CLOCK).minusMonths(1)
        );
        when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(stale));
        when(reviews.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(stale));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                        PartnerEligibilityReviewDecision.REJECT,
                        null,
                        PartnerEligibilityReviewReason.INSUFFICIENT_SOURCE_EVIDENCE
                ))
        );

        assertEquals("PARTNER_ELIGIBILITY_REVIEW_STALE", exception.getErrorCode());
    }

    @Test
    void exactTerminalReplayReturnsExistingProjectionWithoutNewEffects() {
        PartnerEligibilityReview approved = pending().approve(
                employee("IDENTITY-SECRET"), PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED,
                ACTOR_ID, LocalDateTime.now(CLOCK)
        );
        when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(approved));
        when(reviews.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(approved));
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee("IDENTITY-SECRET")));

        var result = service.decide(REVIEW_ID, new PartnerEligibilityReviewDecisionRequest(
                PartnerEligibilityReviewDecision.APPROVE,
                EMPLOYEE_ID,
                PartnerEligibilityReviewReason.CURRENT_EMPLOYEE_CONFIRMED
        ));

        assertEquals("APPROVED", result.status());
        verify(reviews, never()).save(any());
        verify(links, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    private static PartnerEligibilityReview pending() {
        return PartnerEligibilityReview.pending(
                REVIEW_ID, CUSTOMER_ID, COMPANY_ID, "2026-09", BATCH_ID,
                EmployeeVerificationOutcome.NOT_FOUND, "EMP-001", LocalDateTime.now(CLOCK)
        );
    }

    private static PartnerCompany company() {
        return new PartnerCompany(
                COMPANY_ID, "ACME", "Acme Ltd", PartnerCompanyStatus.ACTIVE,
                new BigDecimal("20000000.00")
        );
    }

    private static PartnerEmployeeImportBatch batch() {
        return new PartnerEmployeeImportBatch(
                BATCH_ID, COMPANY_ID, "2026-09", PartnerEmployeeImportBatchStatus.COMPLETED, 1, 0
        );
    }

    private static PartnerEmployee employee(String identityReference) {
        return new PartnerEmployee(
                EMPLOYEE_ID, COMPANY_ID, BATCH_ID, "EMP-001", identityReference,
                new BigDecimal("12000000.00"), new BigDecimal("4000000.00"),
                PartnerEmployeeStatus.ACTIVE, true
        );
    }

    private static CustomerIdentityEvidenceSnapshot identity() {
        return new CustomerIdentityEvidenceSnapshot(CUSTOMER_ID, true, true, "IDENTITY-SECRET");
    }

    private static AuthenticatedUser actor() {
        return new AuthenticatedUser(
                ACTOR_ID, "admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("partner:read", "partner:manage")
        );
    }
}

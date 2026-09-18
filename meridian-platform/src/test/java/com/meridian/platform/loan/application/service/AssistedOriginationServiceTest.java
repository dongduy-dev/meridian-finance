package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CreateAssistedOriginationCaseRequest;
import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistedOriginationServiceTest {
    private final AssistedOriginationCaseRepository cases = mock(AssistedOriginationCaseRepository.class);
    private final CustomerReadinessPort customers = mock(CustomerReadinessPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private AssistedOriginationService service;

    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of("loan:originate:staff")));
        when(cases.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new AssistedOriginationService(cases, customers, users, audits,
                Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsUclAndCollateralWithoutCustomerOrLoanApplicationEffects() {
        assertEquals("UNSECURED_CONSUMER_LOAN", service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.UNSECURED_CONSUMER_LOAN, null)).productCode());
        assertEquals("COLLATERAL_LOAN", service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.COLLATERAL_LOAN, null)).productCode());
        ArgumentCaptor<BusinessAuditEvent> audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits, org.mockito.Mockito.times(2)).publish(audit.capture());
        assertEquals(BusinessAuditAction.ASSISTED_ORIGINATION_CASE_CREATED,
                audit.getAllValues().getFirst().entries().getFirst().action());
    }

    @Test
    void rejectsSalaryAdvanceAndNonStaff() {
        assertThrows(BusinessRuleViolationException.class, () -> service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.SALARY_ADVANCE, null)));
        when(users.currentUser()).thenReturn(actor("CUSTOMER", UUID.randomUUID(), Set.of("loan:originate:staff")));
        assertThrows(AuthorizationException.class, () -> service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.COLLATERAL_LOAN, null)));
    }

    @Test
    void rejectsStaffWithoutPermissionOrWithCustomerContext() {
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of()));
        assertThrows(AuthorizationException.class, () -> service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.UNSECURED_CONSUMER_LOAN, null)));

        when(users.currentUser()).thenReturn(actor("STAFF", UUID.randomUUID(), Set.of("loan:originate:staff")));
        assertThrows(AuthorizationException.class, () -> service.createCase(
                new CreateAssistedOriginationCaseRequest(ProductCode.COLLATERAL_LOAN, null)));
    }

    @Test
    void missingOrInactiveCustomerAssociationFailsSafely() {
        UUID missing = UUID.randomUUID(); UUID inactive = UUID.randomUUID();
        when(customers.findReadinessByCustomerId(missing)).thenReturn(Optional.empty());
        when(customers.findReadinessByCustomerId(inactive)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(inactive, false, false, false, "UNVERIFIED")));

        assertThrows(EntityNotFoundException.class, () -> service.associateCustomer(UUID.randomUUID(), missing));
        assertThrows(BusinessStateConflictException.class,
                () -> service.associateCustomer(UUID.randomUUID(), inactive));
    }

    @Test
    void abandonedCaseBlocksCustomerReassociation() {
        UUID customerId = UUID.randomUUID(); UUID caseId = UUID.randomUUID();
        when(customers.findReadinessByCustomerId(customerId)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(customerId, true, false, false, "UNVERIFIED")));
        AssistedOriginationCase abandoned = new AssistedOriginationCase(
                caseId, ProductCode.UNSECURED_CONSUMER_LOAN, null, AssistedOriginationCaseStatus.ABANDONED,
                users.currentUser().userId(), java.time.LocalDateTime.of(2026, 9, 17, 8, 0),
                java.time.LocalDateTime.of(2026, 9, 17, 8, 1), java.time.LocalDateTime.of(2026, 9, 17, 8, 1));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(abandoned));

        assertThrows(BusinessStateConflictException.class, () -> service.associateCustomer(caseId, customerId));
    }

    @Test
    void validatesSelectedCustomerAndBlocksEvidenceAfterAbandonment() {
        UUID customerId = UUID.randomUUID(); UUID caseId = UUID.randomUUID();
        when(customers.findReadinessByCustomerId(customerId)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(customerId, true, false, false, "UNVERIFIED")));
        AssistedOriginationCase opened = new AssistedOriginationCase(
                caseId, ProductCode.COLLATERAL_LOAN, null, AssistedOriginationCaseStatus.OPEN,
                users.currentUser().userId(), java.time.LocalDateTime.of(2026, 9, 17, 8, 0),
                java.time.LocalDateTime.of(2026, 9, 17, 8, 0), null);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(opened), Optional.of(opened.abandon(
                java.time.LocalDateTime.of(2026, 9, 17, 8, 1))));

        assertEquals(customerId, service.associateCustomer(caseId, customerId).customerId());
        assertThrows(BusinessStateConflictException.class, () -> service.authorizeEvidenceMutation(caseId));
    }

    private static AuthenticatedUser actor(String type, UUID customerId, Set<String> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), "actor@meridian.local", type, customerId, Set.of(), permissions);
    }
}

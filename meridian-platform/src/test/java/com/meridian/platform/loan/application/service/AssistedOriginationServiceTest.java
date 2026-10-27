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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(AssistedOriginationCaseStatus.class)
    void evidenceSpiPreservesHistoricalIdentityAndReadVersusMutationRules(AssistedOriginationCaseStatus status) {
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of("loan:originate:staff", "customer:identity:verify")));
        for (ProductCode product : java.util.List.of(ProductCode.UNSECURED_CONSUMER_LOAN, ProductCode.COLLATERAL_LOAN)) {
            UUID id = UUID.randomUUID(), customer = UUID.randomUUID();
            var at = java.time.LocalDateTime.of(2026, 10, 27, 12, 0);
            var value = new AssistedOriginationCase(id, product, customer, status, users.currentUser().userId(), at, at,
                    status == AssistedOriginationCaseStatus.OPEN ? null : at,
                    status == AssistedOriginationCaseStatus.COMPLETED ? UUID.randomUUID() : null);
            when(cases.findById(id)).thenReturn(Optional.of(value));
            when(cases.findByIdForUpdate(id)).thenReturn(Optional.of(value));
            var spi = new com.meridian.platform.loan.infrastructure.adapter.in.document.AssistedOriginationEvidenceAuthorizationAdapter(service);
            assertEquals(product.name(), spi.authorizeRead(id).productCode());
            org.mockito.Mockito.verify(cases, org.mockito.Mockito.never()).findByIdForUpdate(id);
            assertEquals(customer, spi.authorizeIdentityVerification(id, false));
            if (status == AssistedOriginationCaseStatus.OPEN) {
                assertEquals(id, spi.authorizeMutation(id).caseId());
                assertEquals(customer, spi.authorizeIdentityVerification(id, true));
            } else {
                assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", assertThrows(BusinessStateConflictException.class,
                        () -> spi.authorizeMutation(id)).getErrorCode());
                assertEquals("ASSISTED_ORIGINATION_CASE_NOT_OPEN", assertThrows(BusinessStateConflictException.class,
                        () -> spi.authorizeIdentityVerification(id, true)).getErrorCode());
            }
        }
    }

    @Test
    void identityEvidenceRequiresItsExistingActorShapeSelectedCustomerAndCase() {
        UUID id = UUID.randomUUID();
        var spi = new com.meridian.platform.loan.infrastructure.adapter.in.document.AssistedOriginationEvidenceAuthorizationAdapter(service);
        for (var denied : java.util.List.of(actor("STAFF", null, Set.of()),
                actor("CUSTOMER", UUID.randomUUID(), Set.of("customer:identity:verify")),
                actor("STAFF", UUID.randomUUID(), Set.of("customer:identity:verify")))) {
            when(users.currentUser()).thenReturn(denied);
            assertEquals("IDENTITY_VERIFICATION_ACCESS_DENIED", assertThrows(AuthorizationException.class,
                    () -> spi.authorizeIdentityVerification(id, false)).getErrorCode());
        }
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of("customer:identity:verify")));
        assertEquals("ASSISTED_ORIGINATION_CASE_NOT_FOUND", assertThrows(EntityNotFoundException.class,
                () -> spi.authorizeIdentityVerification(id, false)).getErrorCode());
        var at = java.time.LocalDateTime.of(2026, 10, 27, 12, 0);
        UUID staffId = users.currentUser().userId();
        when(cases.findByIdForUpdate(id)).thenReturn(Optional.of(new AssistedOriginationCase(id,
                ProductCode.COLLATERAL_LOAN, null, AssistedOriginationCaseStatus.OPEN, staffId, at, at, null)));
        assertEquals("ASSISTED_ORIGINATION_CUSTOMER_REQUIRED", assertThrows(BusinessStateConflictException.class,
                () -> spi.authorizeIdentityVerification(id, false)).getErrorCode());
    }

    private static AuthenticatedUser actor(String type, UUID customerId, Set<String> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), "actor@meridian.local", type, customerId, Set.of(), permissions);
    }
}

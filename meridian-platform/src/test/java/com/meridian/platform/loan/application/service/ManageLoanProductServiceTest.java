package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ChangeLoanProductActivationRequest;
import com.meridian.platform.loan.application.dto.UpdateLoanProductLimitsRequest;
import com.meridian.platform.loan.application.mapper.AdminLoanProductMapper;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageLoanProductServiceTest {
    private static final UUID PRODUCT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private final LoanProductRepository products = mock(LoanProductRepository.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private ManageLoanProductService service;

    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                ACTOR_ID, "product.admin@meridian.local", "STAFF", null,
                Set.of(), Set.of("loan:product:manage")
        ));
        when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ManageLoanProductService(
                products, new AdminLoanProductMapper(), users, audits,
                Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void updatesLimitsUnderLockAndAuditsActorProductAndOperation() {
        when(products.findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product(true)));

        var result = service.updateLimits("unsecured_consumer_loan", new UpdateLoanProductLimitsRequest(
                new BigDecimal("750000.00"), new BigDecimal("60000000.00")
        ));

        assertEquals(new BigDecimal("750000.00"), result.minAmount());
        verify(products).findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN);
        verify(products).save(any());
        ArgumentCaptor<BusinessAuditEvent> audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(ACTOR_ID, audit.getValue().operationContext().actorUserId());
        assertEquals(Instant.parse("2026-09-16T08:00:00Z"),
                audit.getValue().operationContext().occurredAt().toInstant(ZoneOffset.UTC));
        assertEquals(BusinessAuditAction.LOAN_PRODUCT_LIMITS_UPDATED,
                audit.getValue().entries().getFirst().action());
        assertEquals(BusinessAuditEntityType.LOAN_PRODUCT,
                audit.getValue().entries().getFirst().entityType());
        assertEquals(PRODUCT_ID, audit.getValue().entries().getFirst().entityId());
        assertEquals("UNSECURED_CONSUMER_LOAN",
                audit.getValue().entries().getFirst().payload().values().get("productCode"));
    }

    @Test
    void sameLimitsAreNoOpWithoutSaveOrAudit() {
        when(products.findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product(true)));

        service.updateLimits("UNSECURED_CONSUMER_LOAN", new UpdateLoanProductLimitsRequest(
                new BigDecimal("1000000.00"), new BigDecimal("50000000.0")
        ));

        verify(products, never()).save(any());
        verify(audits, never()).publish(any());
        verify(users, never()).currentUser();
    }

    @Test
    void rejectsInvalidLimitConfigurationBeforePersistence() {
        when(products.findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product(true)));

        for (UpdateLoanProductLimitsRequest request : new UpdateLoanProductLimitsRequest[] {
                new UpdateLoanProductLimitsRequest(new BigDecimal("-0.01"), new BigDecimal("1.00")),
                new UpdateLoanProductLimitsRequest(new BigDecimal("1.00"), new BigDecimal("-0.01")),
                new UpdateLoanProductLimitsRequest(new BigDecimal("2.00"), new BigDecimal("1.00")),
                new UpdateLoanProductLimitsRequest(new BigDecimal("1.001"), new BigDecimal("2.00")),
                new UpdateLoanProductLimitsRequest(new BigDecimal("100000000000000000.00"),
                        new BigDecimal("100000000000000000.00"))
        }) {
            BusinessRuleViolationException error = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> service.updateLimits("UNSECURED_CONSUMER_LOAN", request)
            );
            assertEquals("INVALID_PRODUCT_LIMITS", error.getErrorCode());
        }
        verify(products, never()).save(any());
        verify(audits, never()).publish(any());
    }

    @Test
    void activationUsesExplicitOutcomeActionsAndSameStateIsNoOp() {
        when(products.findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product(true)));

        var result = service.changeActivation(
                "UNSECURED_CONSUMER_LOAN", new ChangeLoanProductActivationRequest(false)
        );

        assertFalse(result.active());
        ArgumentCaptor<BusinessAuditEvent> audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(BusinessAuditAction.LOAN_PRODUCT_DEACTIVATED,
                audit.getValue().entries().getFirst().action());

        var alreadyActive = service.changeActivation(
                "UNSECURED_CONSUMER_LOAN", new ChangeLoanProductActivationRequest(true)
        );

        assertTrue(alreadyActive.active());
        verify(products).save(any());
        verify(audits).publish(any());

        when(products.findByProductCodeForUpdate(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(Optional.of(product(false)));
        var activated = service.changeActivation(
                "UNSECURED_CONSUMER_LOAN", new ChangeLoanProductActivationRequest(true)
        );

        assertTrue(activated.active());
        ArgumentCaptor<BusinessAuditEvent> activationAudit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits, org.mockito.Mockito.times(2)).publish(activationAudit.capture());
        assertEquals(BusinessAuditAction.LOAN_PRODUCT_ACTIVATED,
                activationAudit.getAllValues().getLast().entries().getFirst().action());
    }

    @Test
    void distinguishesUnknownCodeFromMissingSupportedProduct() {
        assertEquals("PRODUCT_CODE_NOT_FOUND", assertThrows(
                EntityNotFoundException.class,
                () -> service.changeActivation("UNKNOWN", new ChangeLoanProductActivationRequest(true))
        ).getErrorCode());

        when(products.findByProductCodeForUpdate(ProductCode.COLLATERAL_LOAN)).thenReturn(Optional.empty());
        assertEquals("PRODUCT_NOT_FOUND", assertThrows(
                EntityNotFoundException.class,
                () -> service.changeActivation("COLLATERAL_LOAN", new ChangeLoanProductActivationRequest(true))
        ).getErrorCode());
    }

    private static LoanProduct product(boolean active) {
        return new LoanProduct(
                PRODUCT_ID,
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                "Unsecured Consumer Loan",
                "Flexible personal lending.",
                active,
                new BigDecimal("1000000.00"),
                new BigDecimal("50000000.00")
        );
    }
}

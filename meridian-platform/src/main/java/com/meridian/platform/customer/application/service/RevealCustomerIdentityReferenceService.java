package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.RevealCustomerIdentityReferenceUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Service
public class RevealCustomerIdentityReferenceService implements RevealCustomerIdentityReferenceUseCase {
    private final CustomerRepository customers;
    private final CustomerSensitiveValueProtector protector;
    private final BusinessAuditPublisher audit;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public RevealCustomerIdentityReferenceService(CustomerRepository customers,
            CustomerSensitiveValueProtector protector, BusinessAuditPublisher audit,
            CurrentUserProvider currentUser, Clock clock) {
        this.customers = customers;
        this.protector = protector;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result reveal(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        var actor = currentUser.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.roles().contains("LOAN_OFFICER") || !actor.hasPermission("loan:read")
                || !actor.hasPermission("customer:read") || !actor.hasPermission("customer:identity:reveal")) {
            throw new AuthorizationException("CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED",
                    "Customer Identity Reference access is denied.");
        }
        var customer = customers.findById(command.customerId()).orElseThrow(
                RevealCustomerIdentityReferenceService::unavailable);
        if (!Objects.equals(customer.id(), command.customerId()) || !customer.hasCompleteProfile()
                || !Objects.equals(customer.id(), customer.profile().customerId())) throw unavailable();
        var protectedValue = customer.profile().identityReference();
        byte[] plaintext = null;
        String reference;
        try {
            plaintext = protector.revealToBytes(protectedValue);
            reference = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext)).toString();
            if (reference.isBlank() || reference.length() > 100
                    || reference.chars().anyMatch(Character::isISOControl)
                    || !reference.endsWith(protectedValue.lastFour())) throw unavailable();
        } catch (RuntimeException | CharacterCodingException exception) {
            throw unavailable();
        } finally {
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
        audit.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), LocalDateTime.now(clock)),
                new BusinessAuditEntry(BusinessAuditAction.CUSTOMER_IDENTITY_REFERENCE_REVEALED,
                        BusinessAuditEntityType.CUSTOMER, customer.id(), BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, customer.id())
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, command.loanApplicationId()).build())));
        return new Result(reference);
    }

    private static BusinessStateConflictException unavailable() {
        return new BusinessStateConflictException("CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE",
                "The stored Customer Identity Reference is unavailable.");
    }
}

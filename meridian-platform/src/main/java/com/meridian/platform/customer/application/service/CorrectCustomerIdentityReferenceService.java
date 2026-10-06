package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CorrectIdentityReferenceRequest;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.CorrectCustomerIdentityReferenceUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class CorrectCustomerIdentityReferenceService implements CorrectCustomerIdentityReferenceUseCase {
    private final CustomerRepository customers;
    private final CustomerSensitiveValueProtector protector;
    private final CurrentUserProvider users;
    private final CustomerMapper mapper;
    private final BusinessAuditPublisher audits;
    private final Clock clock;

    public CorrectCustomerIdentityReferenceService(CustomerRepository customers, CustomerSensitiveValueProtector protector,
            CurrentUserProvider users, CustomerMapper mapper, BusinessAuditPublisher audits, Clock clock) {
        this.customers = customers; this.protector = protector; this.users = users;
        this.mapper = mapper; this.audits = audits; this.clock = clock;
    }

    @Override
    @Transactional
    public CustomerDto correctOwn(CorrectIdentityReferenceRequest request) {
        var actor = users.currentUser();
        if (!"CUSTOMER".equals(actor.userType()) || actor.optionalCustomerId().isEmpty()
                || !actor.hasPermission("customer:profile:write:own")) throw denied();
        return correct(actor.requireCustomerId(), request, actor);
    }

    @Override
    @Transactional
    public CustomerDto correctForIntake(UUID customerId, CorrectIdentityReferenceRequest request) {
        var actor = users.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("customer:intake:manage")) throw denied();
        return correct(Objects.requireNonNull(customerId), request, actor);
    }

    private CustomerDto correct(UUID customerId, CorrectIdentityReferenceRequest request, AuthenticatedUser actor) {
        Objects.requireNonNull(request);
        if (request.identityReference() == null || request.identityReference().isBlank() || request.identityReference().length() > 100)
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "Enter an Identity Reference of at most 100 characters.");
        var customer = customers.findByIdForUpdate(customerId).orElseThrow(() ->
                new EntityNotFoundException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        customer.requireIdentityReferenceCorrectionAllowed();
        var replacement = protector.protectIdentityReference(request.identityReference());
        if (customers.existsByIdentityReferenceFingerprintAndCustomerIdNot(replacement.fingerprint(), customerId))
            throw new BusinessStateConflictException("IDENTITY_REFERENCE_ALREADY_IN_USE",
                    "Identity reference is already associated with another customer.");
        // An identical correction is a safe no-op, including after an uncertain response.
        if (customer.profile().identityReference().fingerprint().equals(replacement.fingerprint())) return mapper.toCustomerDto(customer);
        var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var saved = customers.save(customer.correctIdentityReference(replacement, now));
        audits.publish(BusinessAuditEvent.single(BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(BusinessAuditAction.CUSTOMER_IDENTITY_REFERENCE_CORRECTED,
                        BusinessAuditEntityType.CUSTOMER, saved.id(), BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, saved.id())
                        .put(BusinessAuditPayloadKey.IDENTITY_VERIFICATION_STATUS, saved.verificationStatus()).build())));
        return mapper.toCustomerDto(saved);
    }

    private static AuthorizationException denied() {
        return new AuthorizationException("CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED", "Customer Identity Reference access is denied.");
    }
}

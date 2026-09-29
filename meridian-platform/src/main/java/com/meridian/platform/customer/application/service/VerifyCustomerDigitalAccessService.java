package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerDigitalAccessCandidate;
import com.meridian.platform.customer.application.port.in.VerifyCustomerDigitalAccessUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class VerifyCustomerDigitalAccessService implements VerifyCustomerDigitalAccessUseCase {
    private final CustomerRepository customers;
    private final CustomerSensitiveValueProtector protector;

    public VerifyCustomerDigitalAccessService(CustomerRepository customers,
                                              CustomerSensitiveValueProtector protector) {
        this.customers = Objects.requireNonNull(customers);
        this.protector = Objects.requireNonNull(protector);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireExistingCustomer(UUID customerId) {
        customers.findById(Objects.requireNonNull(customerId)).orElseThrow(VerifyCustomerDigitalAccessService::notFound);
    }

    @Override
    @Transactional
    public CustomerDigitalAccessCandidate verifyForActivation(UUID customerId, String identityReference) {
        Customer customer = customers.findByIdForUpdate(Objects.requireNonNull(customerId))
                .orElseThrow(VerifyCustomerDigitalAccessService::notFound);
        if (!customer.isActive()) {
            throw new BusinessStateConflictException("CUSTOMER_NOT_ACTIVE", "Customer must be active for this operation.");
        }
        if (customer.profile() == null || customer.profile().identityReference() == null) {
            throw ownershipNotVerified();
        }
        String presentedFingerprint = protector.protectIdentityReference(identityReference).fingerprint();
        if (!customer.profile().identityReference().fingerprint().equals(presentedFingerprint)) {
            throw ownershipNotVerified();
        }
        return new CustomerDigitalAccessCandidate(customer.id(), customer.profile().fullName());
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("CUSTOMER_NOT_FOUND", "Customer was not found.");
    }

    private static BusinessRuleViolationException ownershipNotVerified() {
        return new BusinessRuleViolationException("CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED",
                "Customer identity could not be verified for digital access.");
    }
}

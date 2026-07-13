package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.UpdateOwnCustomerProfileUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateOwnCustomerProfileService implements UpdateOwnCustomerProfileUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerSensitiveValueProtector sensitiveValueProtector;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;

    public UpdateOwnCustomerProfileService(
            CustomerRepository customerRepository,
            CustomerSensitiveValueProtector sensitiveValueProtector,
            CurrentUserProvider currentUserProvider,
            CustomerMapper customerMapper,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.sensitiveValueProtector = sensitiveValueProtector;
        this.currentUserProvider = currentUserProvider;
        this.customerMapper = customerMapper;
        this.businessAuditPublisher = businessAuditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CustomerDto updateOwnProfile(UpdateCustomerProfileRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);

        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!customer.isActive()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active for this operation."
            );
        }

        CustomerProfile previousProfile = customer.profile();
        ProfileCompletionStatus previousCompletionStatus = customer.profileCompletionStatus();
        CustomerProfile newProfile = new CustomerProfile(
                previousProfile == null ? null : previousProfile.id(),
                customerId,
                request.fullName(),
                resolveIdentityReference(previousProfile, request.identityReference(), customerId),
                request.phoneNumber(),
                request.residentialAddress(),
                request.employmentStatus(),
                request.employerName(),
                request.termsConsentAccepted(),
                request.dataProcessingConsentAccepted(),
                previousProfile == null ? null : previousProfile.createdAt(),
                now
        );

        Customer updatedCustomer = customer.updateProfile(newProfile, now);
        Customer savedCustomer = customerRepository.save(updatedCustomer);

        businessAuditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(UUID.randomUUID(), currentUser.userId(), now),
                auditEntries(savedCustomer, previousProfile == null, previousCompletionStatus)
        ));

        return customerMapper.toCustomerDto(savedCustomer);
    }

    private ProtectedSensitiveValue resolveIdentityReference(
            CustomerProfile previousProfile,
            String requestedIdentityReference,
            UUID customerId
    ) {
        if (requestedIdentityReference != null && !requestedIdentityReference.isBlank()) {
            ProtectedSensitiveValue protectedValue = sensitiveValueProtector.protectIdentityReference(requestedIdentityReference);
            if (previousProfile != null
                    && previousProfile.identityReference().fingerprint().equals(protectedValue.fingerprint())) {
                return protectedValue;
            }
            if (previousProfile == null || !previousProfile.isComplete()) {
                assertIdentityReferenceNotOwnedByAnother(protectedValue, customerId);
            }
            return protectedValue;
        }
        if (previousProfile != null) {
            return previousProfile.identityReference();
        }
        throw new BusinessRuleViolationException(
                "PROFILE_INCOMPLETE",
                "Customer profile requires identity reference before it can be completed."
        );
    }

    private void assertIdentityReferenceNotOwnedByAnother(
            ProtectedSensitiveValue protectedValue,
            UUID customerId
    ) {
        if (customerRepository.existsByIdentityReferenceFingerprintAndCustomerIdNot(
                protectedValue.fingerprint(),
                customerId
        )) {
            throw identityReferenceAlreadyInUse();
        }
    }

    private BusinessStateConflictException identityReferenceAlreadyInUse() {
        return new BusinessStateConflictException(
                "IDENTITY_REFERENCE_ALREADY_IN_USE",
                "Identity reference is already associated with another customer."
        );
    }

    private List<BusinessAuditEntry> auditEntries(
            Customer customer,
            boolean createdProfile,
            ProfileCompletionStatus previousCompletionStatus
    ) {
        List<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(profileAuditEntry(
                createdProfile
                        ? BusinessAuditAction.CUSTOMER_PROFILE_CREATED
                        : BusinessAuditAction.CUSTOMER_PROFILE_UPDATED,
                customer
        ));
        if (previousCompletionStatus != ProfileCompletionStatus.COMPLETE
                && customer.profileCompletionStatus() == ProfileCompletionStatus.COMPLETE) {
            entries.add(profileAuditEntry(BusinessAuditAction.CUSTOMER_PROFILE_COMPLETED, customer));
        }
        return entries;
    }

    private BusinessAuditEntry profileAuditEntry(BusinessAuditAction action, Customer customer) {
        return new BusinessAuditEntry(
                action,
                BusinessAuditEntityType.CUSTOMER,
                customer.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, customer.id())
                        .put(BusinessAuditPayloadKey.PROFILE_COMPLETION_STATUS, customer.profileCompletionStatus())
                        .build()
        );
    }
}

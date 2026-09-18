package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CreateStaffAssistedCustomerRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.StaffCustomerSearchRequest;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.StaffCustomerIntakeUseCase;
import com.meridian.platform.customer.application.port.out.CustomerNumberSequenceRepository;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
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
import com.meridian.platform.shared.domain.exception.AuthorizationException;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class StaffCustomerIntakeService implements StaffCustomerIntakeUseCase {

    private static final String READ_PERMISSION = "customer:read";
    private static final String MANAGE_PERMISSION = "customer:intake:manage";

    private final CustomerRepository customers;
    private final CustomerNumberSequenceRepository customerNumbers;
    private final CustomerSensitiveValueProtector protector;
    private final CurrentUserProvider currentUsers;
    private final CustomerMapper mapper;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public StaffCustomerIntakeService(
            CustomerRepository customers,
            CustomerNumberSequenceRepository customerNumbers,
            CustomerSensitiveValueProtector protector,
            CurrentUserProvider currentUsers,
            CustomerMapper mapper,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.customers = customers;
        this.customerNumbers = customerNumbers;
        this.protector = protector;
        this.currentUsers = currentUsers;
        this.mapper = mapper;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto search(StaffCustomerSearchRequest request) {
        requireStaff(READ_PERMISSION);
        Objects.requireNonNull(request, "request must not be null");
        boolean byNumber = request.customerNumber() != null && !request.customerNumber().isBlank();
        boolean byIdentity = request.identityReference() != null && !request.identityReference().isBlank();
        if (byNumber == byIdentity) {
            throw new BusinessRuleViolationException(
                    "INVALID_CUSTOMER_SEARCH",
                    "Provide exactly one exact Customer number or identity reference."
            );
        }
        Optional<Customer> result = byNumber
                ? customers.findByCustomerNumber(request.customerNumber().trim())
                : customers.findByIdentityReferenceFingerprint(
                        protector.protectIdentityReference(request.identityReference()).fingerprint()
                );
        return mapper.toCustomerDto(result.orElseThrow(StaffCustomerIntakeService::customerNotFound));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto getCustomer(UUID customerId) {
        requireStaff(READ_PERMISSION);
        return mapper.toCustomerDto(activeCustomer(customerId, false));
    }

    @Override
    @Transactional
    public CustomerDto createCustomer(CreateStaffAssistedCustomerRequest request) {
        AuthenticatedUser actor = requireStaff(MANAGE_PERMISSION);
        Objects.requireNonNull(request, "request must not be null");
        LocalDateTime now = LocalDateTime.now(clock);
        ProtectedSensitiveValue identity = protector.protectIdentityReference(request.identityReference());
        if (customers.existsByIdentityReferenceFingerprint(identity.fingerprint())) {
            throw identityAlreadyInUse();
        }
        UUID customerId = UUID.randomUUID();
        CustomerProfile profile = new CustomerProfile(
                UUID.randomUUID(), customerId, request.fullName(), identity, request.phoneNumber(),
                request.residentialAddress(), request.employmentStatus(), request.employerName(),
                request.termsConsentAccepted(), request.dataProcessingConsentAccepted(), now, now
        );
        Customer customer = new Customer(
                customerId,
                "CUS-%09d".formatted(customerNumbers.nextCustomerNumberSequence()),
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                now,
                now
        ).updateProfile(profile, now);
        Customer saved = customers.save(customer);
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.STAFF_ASSISTED_CUSTOMER_CREATED,
                        BusinessAuditEntityType.CUSTOMER,
                        saved.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.CUSTOMER_ID, saved.id())
                                .put(BusinessAuditPayloadKey.PROFILE_COMPLETION_STATUS, saved.profileCompletionStatus())
                                .build()
                )
        ));
        return mapper.toCustomerDto(saved);
    }

    @Override
    @Transactional
    public CustomerDto updateProfile(UUID customerId, UpdateCustomerProfileRequest request) {
        AuthenticatedUser actor = requireStaff(MANAGE_PERMISSION);
        Objects.requireNonNull(request, "request must not be null");
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomer(customerId, true);
        CustomerProfile previous = customer.profile();
        ProfileCompletionStatus previousCompletion = customer.profileCompletionStatus();
        CustomerProfile profile = new CustomerProfile(
                previous == null ? null : previous.id(),
                customer.id(),
                request.fullName(),
                resolveIdentity(previous, request.identityReference(), customer.id()),
                request.phoneNumber(), request.residentialAddress(), request.employmentStatus(),
                request.employerName(), request.termsConsentAccepted(), request.dataProcessingConsentAccepted(),
                previous == null ? null : previous.createdAt(), now
        );
        Customer saved = customers.save(customer.updateProfile(profile, now));
        List<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(profileAudit(previous == null
                ? BusinessAuditAction.CUSTOMER_PROFILE_CREATED
                : BusinessAuditAction.CUSTOMER_PROFILE_UPDATED, saved));
        if (previousCompletion != ProfileCompletionStatus.COMPLETE
                && saved.profileCompletionStatus() == ProfileCompletionStatus.COMPLETE) {
            entries.add(profileAudit(BusinessAuditAction.CUSTOMER_PROFILE_COMPLETED, saved));
        }
        auditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now), entries));
        return mapper.toCustomerDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerBankAccountDto> getBankAccounts(UUID customerId) {
        requireStaff(MANAGE_PERMISSION);
        return activeCustomer(customerId, false).bankAccounts().stream().map(mapper::toBankAccountDto).toList();
    }

    @Override
    @Transactional
    public CustomerBankAccountDto addBankAccount(UUID customerId, AddCustomerBankAccountRequest request) {
        AuthenticatedUser actor = requireStaff(MANAGE_PERMISSION);
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomer(customerId, true);
        CustomerBankAccount created = new CustomerBankAccount(
                UUID.randomUUID(), customer.id(), request.bankCode(), request.bankNameSnapshot(),
                request.accountHolderName(), protector.protectBankAccountNumber(request.bankCode(), request.accountNumber()),
                CustomerBankAccountStatus.ACTIVE, false, now, now, null
        );
        CustomerBankAccount saved = account(customers.save(customer.addBankAccount(created, now)), created.id());
        publishBankAudit(actor, now, BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_ADDED, saved, null, null);
        return mapper.toBankAccountDto(saved);
    }

    @Override
    @Transactional
    public CustomerBankAccountDto makePrimary(UUID customerId, UUID bankAccountId) {
        AuthenticatedUser actor = requireStaff(MANAGE_PERMISSION);
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomer(customerId, true);
        UUID previousPrimary = customer.bankAccounts().stream().filter(CustomerBankAccount::isPrimaryActive)
                .map(CustomerBankAccount::id).findFirst().orElse(null);
        CustomerBankAccount saved = account(
                customers.save(customer.makePrimaryBankAccount(bankAccountId, now)), bankAccountId);
        if (!Objects.equals(previousPrimary, saved.id())) {
            publishBankAudit(actor, now, BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY,
                    saved, previousPrimary, saved.id());
        }
        return mapper.toBankAccountDto(saved);
    }

    @Override
    @Transactional
    public CustomerBankAccountDto deactivate(UUID customerId, UUID bankAccountId) {
        AuthenticatedUser actor = requireStaff(MANAGE_PERMISSION);
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomer(customerId, true);
        CustomerBankAccount before = account(customer, bankAccountId);
        CustomerBankAccount saved = account(customers.save(customer.deactivateBankAccount(bankAccountId, now)), bankAccountId);
        if (before.isActive()) {
            publishBankAudit(actor, now, BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_DEACTIVATED,
                    saved, null, null);
        }
        return mapper.toBankAccountDto(saved);
    }

    private AuthenticatedUser requireStaff(String permission) {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(permission)) {
            throw new AuthorizationException("STAFF_CUSTOMER_INTAKE_ACCESS_DENIED",
                    "Staff Customer intake access is denied.");
        }
        return actor;
    }

    private Customer activeCustomer(UUID customerId, boolean forUpdate) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Customer customer = (forUpdate ? customers.findByIdForUpdate(customerId) : customers.findById(customerId))
                .orElseThrow(StaffCustomerIntakeService::customerNotFound);
        if (!customer.isActive()) {
            throw new BusinessStateConflictException("CUSTOMER_NOT_ACTIVE",
                    "Customer must be active for this operation.");
        }
        return customer;
    }

    private ProtectedSensitiveValue resolveIdentity(CustomerProfile previous, String raw, UUID customerId) {
        if (raw != null && !raw.isBlank()) {
            ProtectedSensitiveValue protectedValue = protector.protectIdentityReference(raw);
            if (previous == null || !previous.identityReference().fingerprint().equals(protectedValue.fingerprint())) {
                if (previous == null || !previous.isComplete()) {
                    if (customers.existsByIdentityReferenceFingerprintAndCustomerIdNot(
                            protectedValue.fingerprint(), customerId)) {
                        throw identityAlreadyInUse();
                    }
                }
            }
            return protectedValue;
        }
        if (previous != null) return previous.identityReference();
        throw new BusinessRuleViolationException("PROFILE_INCOMPLETE",
                "Customer profile requires identity reference before it can be completed.");
    }

    private CustomerBankAccount account(Customer customer, UUID accountId) {
        return customer.bankAccounts().stream().filter(account -> account.id().equals(accountId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("BANK_ACCOUNT_NOT_FOUND",
                        "Bank account was not found for the customer."));
    }

    private BusinessAuditEntry profileAudit(BusinessAuditAction action, Customer customer) {
        return new BusinessAuditEntry(action, BusinessAuditEntityType.CUSTOMER, customer.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, customer.id())
                        .put(BusinessAuditPayloadKey.PROFILE_COMPLETION_STATUS, customer.profileCompletionStatus())
                        .build());
    }

    private void publishBankAudit(
            AuthenticatedUser actor, LocalDateTime now, BusinessAuditAction action,
            CustomerBankAccount account, UUID previousPrimary, UUID newPrimary
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, account.customerId())
                .put(BusinessAuditPayloadKey.CUSTOMER_BANK_ACCOUNT_ID, account.id())
                .put(BusinessAuditPayloadKey.BANK_ACCOUNT_STATUS, account.status());
        if (previousPrimary != null) payload.put(BusinessAuditPayloadKey.PREVIOUS_PRIMARY_BANK_ACCOUNT_ID, previousPrimary);
        if (newPrimary != null) payload.put(BusinessAuditPayloadKey.NEW_PRIMARY_BANK_ACCOUNT_ID, newPrimary);
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(action, BusinessAuditEntityType.CUSTOMER_BANK_ACCOUNT, account.id(), payload.build())
        ));
    }

    private static EntityNotFoundException customerNotFound() {
        return new EntityNotFoundException("CUSTOMER_NOT_FOUND", "Customer was not found.");
    }

    private static BusinessStateConflictException identityAlreadyInUse() {
        return new BusinessStateConflictException("IDENTITY_REFERENCE_ALREADY_IN_USE",
                "Identity reference is already associated with another customer.");
    }
}

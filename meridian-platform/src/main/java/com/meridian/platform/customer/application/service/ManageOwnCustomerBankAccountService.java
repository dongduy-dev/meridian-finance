package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ManageOwnCustomerBankAccountService implements ManageOwnCustomerBankAccountUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerSensitiveValueProtector sensitiveValueProtector;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final Clock clock;

    public ManageOwnCustomerBankAccountService(
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
    public CustomerBankAccountDto addBankAccount(AddCustomerBankAccountRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomerForUpdate(currentUser.requireCustomerId());
        CustomerBankAccount account = new CustomerBankAccount(
                UUID.randomUUID(),
                customer.id(),
                request.bankCode(),
                request.bankNameSnapshot(),
                request.accountHolderName(),
                sensitiveValueProtector.protectBankAccountNumber(request.bankCode(), request.accountNumber()),
                CustomerBankAccountStatus.ACTIVE,
                false,
                now,
                now,
                null
        );

        Customer savedCustomer = customerRepository.save(customer.addBankAccount(account, now));
        CustomerBankAccount savedAccount = findAccount(savedCustomer, account.id());
        publishAudit(
                currentUser,
                now,
                BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_ADDED,
                savedAccount,
                null,
                null
        );
        return customerMapper.toBankAccountDto(savedAccount);
    }

    @Override
    @Transactional
    public CustomerBankAccountDto makePrimary(UUID customerBankAccountId) {
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomerForUpdate(currentUser.requireCustomerId());
        UUID previousPrimaryId = currentPrimaryId(customer).orElse(null);

        Customer savedCustomer = customerRepository.save(customer.makePrimaryBankAccount(customerBankAccountId, now));
        CustomerBankAccount savedAccount = findAccount(savedCustomer, customerBankAccountId);
        if (!Objects.equals(previousPrimaryId, savedAccount.id())) {
            publishAudit(
                    currentUser,
                    now,
                    BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY,
                    savedAccount,
                    previousPrimaryId,
                    savedAccount.id()
            );
        }
        return customerMapper.toBankAccountDto(savedAccount);
    }

    @Override
    @Transactional
    public CustomerBankAccountDto deactivate(UUID customerBankAccountId) {
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = activeCustomerForUpdate(currentUser.requireCustomerId());
        CustomerBankAccount before = findAccount(customer, customerBankAccountId);

        Customer savedCustomer = customerRepository.save(customer.deactivateBankAccount(customerBankAccountId, now));
        CustomerBankAccount savedAccount = findAccount(savedCustomer, customerBankAccountId);
        if (before.isActive()) {
            publishAudit(
                    currentUser,
                    now,
                    BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_DEACTIVATED,
                    savedAccount,
                    null,
                    null
            );
        }
        return customerMapper.toBankAccountDto(savedAccount);
    }

    private Customer activeCustomerForUpdate(UUID customerId) {
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
        return customer;
    }

    private Optional<UUID> currentPrimaryId(Customer customer) {
        return customer.bankAccounts().stream()
                .filter(CustomerBankAccount::isPrimaryActive)
                .map(CustomerBankAccount::id)
                .findFirst();
    }

    private CustomerBankAccount findAccount(Customer customer, UUID customerBankAccountId) {
        return customer.bankAccounts().stream()
                .filter(account -> account.id().equals(customerBankAccountId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "BANK_ACCOUNT_NOT_FOUND",
                        "Bank account was not found for the customer."
                ));
    }

    private void publishAudit(
            AuthenticatedUser currentUser,
            LocalDateTime now,
            BusinessAuditAction action,
            CustomerBankAccount account,
            UUID previousPrimaryBankAccountId,
            UUID newPrimaryBankAccountId
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, account.customerId())
                .put(BusinessAuditPayloadKey.CUSTOMER_BANK_ACCOUNT_ID, account.id())
                .put(BusinessAuditPayloadKey.BANK_ACCOUNT_STATUS, account.status());
        if (previousPrimaryBankAccountId != null) {
            payload.put(BusinessAuditPayloadKey.PREVIOUS_PRIMARY_BANK_ACCOUNT_ID, previousPrimaryBankAccountId);
        }
        if (newPrimaryBankAccountId != null) {
            payload.put(BusinessAuditPayloadKey.NEW_PRIMARY_BANK_ACCOUNT_ID, newPrimaryBankAccountId);
        }
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), currentUser.userId(), now),
                new BusinessAuditEntry(
                        action,
                        BusinessAuditEntityType.CUSTOMER_BANK_ACCOUNT,
                        account.id(),
                        payload.build()
                )
        ));
    }
}

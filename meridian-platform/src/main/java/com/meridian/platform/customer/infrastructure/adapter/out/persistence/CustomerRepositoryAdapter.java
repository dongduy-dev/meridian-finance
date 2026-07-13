package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final JpaCustomerRepository customerRepository;
    private final JpaCustomerProfileRepository profileRepository;
    private final JpaCustomerBankAccountRepository bankAccountRepository;

    public CustomerRepositoryAdapter(
            JpaCustomerRepository customerRepository,
            JpaCustomerProfileRepository profileRepository,
            JpaCustomerBankAccountRepository bankAccountRepository
    ) {
        this.customerRepository = customerRepository;
        this.profileRepository = profileRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerJpaEntity customerEntity = customerRepository.findById(customer.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(customer);
                    return existingEntity;
                })
                .orElseGet(() -> new CustomerJpaEntity(customer));
        CustomerJpaEntity savedCustomer = customerRepository.save(customerEntity);

        saveProfile(customer.profile());
        saveBankAccounts(customer.bankAccounts());

        return toDomain(savedCustomer, false);
    }

    @Override
    public Optional<Customer> findById(UUID customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> toDomain(customer, false));
    }

    @Override
    public Optional<Customer> findByIdForUpdate(UUID customerId) {
        return customerRepository.findByIdForUpdate(customerId)
                .map(customer -> toDomain(customer, true));
    }

    private void saveProfile(CustomerProfile profile) {
        if (profile == null) {
            return;
        }
        CustomerProfileJpaEntity profileEntity = profileRepository.findByCustomerId(profile.customerId())
                .map(existingEntity -> {
                    existingEntity.updateFrom(profile);
                    return existingEntity;
                })
                .orElseGet(() -> new CustomerProfileJpaEntity(profile));
        profileRepository.save(profileEntity);
    }

    private void saveBankAccounts(List<CustomerBankAccount> bankAccounts) {
        for (CustomerBankAccount bankAccount : bankAccounts) {
            CustomerBankAccountJpaEntity bankAccountEntity = bankAccount.id() == null
                    ? new CustomerBankAccountJpaEntity(bankAccount)
                    : bankAccountRepository.findById(bankAccount.id())
                            .map(existingEntity -> {
                                existingEntity.updateFrom(bankAccount);
                                return existingEntity;
                            })
                            .orElseGet(() -> new CustomerBankAccountJpaEntity(bankAccount));
            bankAccountRepository.save(bankAccountEntity);
        }
    }

    private Customer toDomain(CustomerJpaEntity customerEntity, boolean lockedChildren) {
        CustomerProfile profile = lockedChildren
                ? profileRepository.findByCustomerIdForUpdate(customerEntity.getId())
                        .map(CustomerProfileJpaEntity::toDomain)
                        .orElse(null)
                : profileRepository.findByCustomerId(customerEntity.getId())
                        .map(CustomerProfileJpaEntity::toDomain)
                        .orElse(null);
        List<CustomerBankAccount> bankAccounts = (lockedChildren
                ? bankAccountRepository.findByCustomerIdForUpdate(customerEntity.getId())
                : bankAccountRepository.findByCustomerIdOrderByCreatedAtAsc(customerEntity.getId()))
                .stream()
                .map(CustomerBankAccountJpaEntity::toDomain)
                .toList();

        return new Customer(
                customerEntity.getId(),
                customerEntity.getCustomerNumber(),
                customerEntity.getStatus(),
                customerEntity.getVerificationStatus(),
                customerEntity.getProfileCompletionStatus(),
                profile,
                bankAccounts,
                customerEntity.getCreatedAt(),
                customerEntity.getUpdatedAt()
        );
    }
}
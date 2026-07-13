package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRepositoryAdapter implements CustomerRepository {

    private static final String IDENTITY_REFERENCE_FINGERPRINT_CONSTRAINT =
            "uq_customer_profiles_identity_reference_fingerprint";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final JpaCustomerRepository customerRepository;
    private final JpaCustomerProfileRepository profileRepository;
    private final JpaCustomerBankAccountRepository bankAccountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public CustomerRepositoryAdapter(
            JpaCustomerRepository customerRepository,
            JpaCustomerProfileRepository profileRepository,
            JpaCustomerBankAccountRepository bankAccountRepository,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager
    ) {
        this.customerRepository = customerRepository;
        this.profileRepository = profileRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerJpaEntity customerEntity = customerRepository.findById(customer.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(customer);
                    return existingEntity;
                })
                .orElseGet(() -> new CustomerJpaEntity(customer));
        UUID savedCustomerId = customerRepository.saveAndFlush(customerEntity).getId();

        boolean profileSaved = saveProfile(customer.profile());
        if (profileSaved) {
            entityManager.clear();
        }
        saveBankAccounts(customer.bankAccounts());

        return findById(savedCustomerId)
                .orElseThrow(() -> new IllegalStateException("Saved customer could not be reloaded."));
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

    @Override
    public boolean existsByIdentityReferenceFingerprintAndCustomerIdNot(String fingerprint, UUID customerId) {
        return profileRepository.existsByIdentityReferenceFingerprintAndCustomerIdNot(fingerprint, customerId);
    }

    private boolean saveProfile(CustomerProfile profile) {
        if (profile == null) {
            return false;
        }
        Optional<CustomerProfileJpaEntity> existingProfile = profileRepository.findByCustomerId(profile.customerId());
        if (existingProfile.isPresent()) {
            updateProfile(existingProfile.orElseThrow().getId(), profile);
        } else {
            insertProfile(profile);
        }
        return true;
    }

    private void insertProfile(CustomerProfile profile) {
        UUID profileId = profile.id() == null ? UUID.randomUUID() : profile.id();
        try {
            int insertedRows = jdbcTemplate.update(
                    """
                            insert into customer_profiles (
                                id,
                                customer_id,
                                full_name,
                                identity_reference_ciphertext,
                                identity_reference_fingerprint,
                                identity_reference_last_four,
                                phone_number,
                                residential_address,
                                employment_status,
                                employer_name,
                                terms_consent_accepted,
                                data_processing_consent_accepted,
                                created_at,
                                updated_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on conflict on constraint uq_customer_profiles_identity_reference_fingerprint do nothing
                            """,
                    profileId,
                    profile.customerId(),
                    profile.fullName(),
                    profile.identityReference().ciphertext(),
                    profile.identityReference().fingerprint(),
                    profile.identityReference().lastFour(),
                    profile.phoneNumber(),
                    profile.residentialAddress(),
                    profile.employmentStatus(),
                    profile.employerName(),
                    profile.termsConsentAccepted(),
                    profile.dataProcessingConsentAccepted(),
                    profile.createdAt(),
                    profile.updatedAt()
            );
            if (insertedRows == 0) {
                throw identityReferenceAlreadyInUse();
            }
        } catch (DataIntegrityViolationException exception) {
            if (isIdentityReferenceFingerprintConstraintViolation(exception)) {
                throw identityReferenceAlreadyInUse();
            }
            throw exception;
        }
    }

    private void updateProfile(UUID profileId, CustomerProfile profile) {
        try {
            jdbcTemplate.update(
                    """
                            update customer_profiles
                            set full_name = ?,
                                identity_reference_ciphertext = ?,
                                identity_reference_fingerprint = ?,
                                identity_reference_last_four = ?,
                                phone_number = ?,
                                residential_address = ?,
                                employment_status = ?,
                                employer_name = ?,
                                terms_consent_accepted = ?,
                                data_processing_consent_accepted = ?,
                                updated_at = ?
                            where id = ?
                            """,
                    profile.fullName(),
                    profile.identityReference().ciphertext(),
                    profile.identityReference().fingerprint(),
                    profile.identityReference().lastFour(),
                    profile.phoneNumber(),
                    profile.residentialAddress(),
                    profile.employmentStatus(),
                    profile.employerName(),
                    profile.termsConsentAccepted(),
                    profile.dataProcessingConsentAccepted(),
                    profile.updatedAt(),
                    profileId
            );
        } catch (DataIntegrityViolationException exception) {
            if (isIdentityReferenceFingerprintConstraintViolation(exception)) {
                throw identityReferenceAlreadyInUse();
            }
            throw exception;
        }
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

    private boolean isIdentityReferenceFingerprintConstraintViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                    && messageContainsIdentityReferenceConstraint(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean messageContainsIdentityReferenceConstraint(SQLException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains(IDENTITY_REFERENCE_FINGERPRINT_CONSTRAINT);
    }

    private BusinessStateConflictException identityReferenceAlreadyInUse() {
        return new BusinessStateConflictException(
                "IDENTITY_REFERENCE_ALREADY_IN_USE",
                "Identity reference is already associated with another customer."
        );
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
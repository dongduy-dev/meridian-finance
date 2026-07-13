package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerRepositoryAdapterTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    @Test
    void returnsIdentityConflictWhenInsertUsesConstraintFallback() {
        Customer customer = completeCustomer("fingerprint-one");
        AdapterFixture fixture = adapterFixture(customer);
        when(fixture.profileRepository.findByCustomerId(customer.id())).thenReturn(Optional.empty());
        when(fixture.jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> fixture.adapter.save(customer)
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals("Identity reference is already associated with another customer.", exception.getMessage());
    }

    @Test
    void translatesKnownIdentityReferenceUniqueConstraintViolation() {
        Customer customer = completeCustomer("fingerprint-one");
        AdapterFixture fixture = adapterFixture(customer);
        when(fixture.profileRepository.findByCustomerId(customer.id()))
                .thenReturn(Optional.of(new CustomerProfileJpaEntity(customer.profile())));
        when(fixture.jdbcTemplate.update(anyString(), any(Object[].class))).thenThrow(new DataIntegrityViolationException(
                "profile unique violation",
                new SQLException(
                        "duplicate key value violates unique constraint \"uq_customer_profiles_identity_reference_fingerprint\"",
                        "23505"
                )
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> fixture.adapter.save(customer)
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals("Identity reference is already associated with another customer.", exception.getMessage());
    }

    @Test
    void rethrowsUnrelatedDataIntegrityViolation() {
        Customer customer = completeCustomer("fingerprint-one");
        AdapterFixture fixture = adapterFixture(customer);
        DataIntegrityViolationException expected = new DataIntegrityViolationException(
                "other unique violation",
                new SQLException(
                        "duplicate key value violates unique constraint \"uq_customer_profiles_customer_id\"",
                        "23505"
                )
        );
        when(fixture.profileRepository.findByCustomerId(customer.id())).thenReturn(Optional.empty());
        when(fixture.jdbcTemplate.update(anyString(), any(Object[].class))).thenThrow(expected);

        DataIntegrityViolationException actual = assertThrows(
                DataIntegrityViolationException.class,
                () -> fixture.adapter.save(customer)
        );

        assertSame(expected, actual);
    }

    private static AdapterFixture adapterFixture(Customer customer) {
        JpaCustomerRepository customerRepository = mock(JpaCustomerRepository.class);
        JpaCustomerProfileRepository profileRepository = mock(JpaCustomerProfileRepository.class);
        JpaCustomerBankAccountRepository bankAccountRepository = mock(JpaCustomerBankAccountRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        CustomerJpaEntity customerEntity = new CustomerJpaEntity(customer);
        when(customerRepository.findById(customer.id())).thenReturn(Optional.of(customerEntity));
        when(customerRepository.saveAndFlush(any(CustomerJpaEntity.class))).thenReturn(customerEntity);
        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter(
                customerRepository,
                profileRepository,
                bankAccountRepository,
                jdbcTemplate,
                entityManager
        );
        return new AdapterFixture(adapter, profileRepository, jdbcTemplate);
    }

    private record AdapterFixture(
            CustomerRepositoryAdapter adapter,
            JpaCustomerProfileRepository profileRepository,
            JdbcTemplate jdbcTemplate
    ) {
    }

    private static Customer completeCustomer(String identityFingerprint) {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.COMPLETE,
                new CustomerProfile(
                        UUID.randomUUID(),
                        CUSTOMER_ID,
                        "Customer Demo",
                        new ProtectedSensitiveValue("ciphertext", identityFingerprint, "0001"),
                        "0901234567",
                        "1 Meridian Street",
                        "SALARIED",
                        "Meridian Partner Co",
                        true,
                        true,
                        NOW,
                        NOW
                ),
                List.of(),
                NOW,
                NOW
        );
    }
}
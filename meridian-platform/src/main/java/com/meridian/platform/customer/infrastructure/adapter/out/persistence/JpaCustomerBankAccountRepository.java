package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCustomerBankAccountRepository extends JpaRepository<CustomerBankAccountJpaEntity, UUID> {

    List<CustomerBankAccountJpaEntity> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

    Optional<CustomerBankAccountJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CustomerBankAccountJpaEntity account where account.customerId = :customerId order by account.createdAt asc")
    List<CustomerBankAccountJpaEntity> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);
}
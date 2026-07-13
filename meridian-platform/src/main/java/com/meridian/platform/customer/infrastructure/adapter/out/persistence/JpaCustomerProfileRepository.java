package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaCustomerProfileRepository extends JpaRepository<CustomerProfileJpaEntity, UUID> {

    Optional<CustomerProfileJpaEntity> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CustomerProfileJpaEntity profile where profile.customerId = :customerId")
    Optional<CustomerProfileJpaEntity> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);
}
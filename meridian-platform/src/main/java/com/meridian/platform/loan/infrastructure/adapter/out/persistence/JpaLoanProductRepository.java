package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanProductRepository extends JpaRepository<LoanProductJpaEntity, UUID> {

    List<LoanProductJpaEntity> findByActiveTrueOrderByNameAsc();

    List<LoanProductJpaEntity> findAllByOrderByProductCodeAsc();

    Optional<LoanProductJpaEntity> findByProductCode(String productCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from LoanProductJpaEntity product where product.productCode = :productCode")
    Optional<LoanProductJpaEntity> findByProductCodeForUpdate(@Param("productCode") String productCode);
}

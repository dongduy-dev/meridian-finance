package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanProductRepository extends JpaRepository<LoanProductJpaEntity, UUID> {

    List<LoanProductJpaEntity> findByActiveTrueOrderByNameAsc();

    Optional<LoanProductJpaEntity> findByProductCode(String productCode);
}
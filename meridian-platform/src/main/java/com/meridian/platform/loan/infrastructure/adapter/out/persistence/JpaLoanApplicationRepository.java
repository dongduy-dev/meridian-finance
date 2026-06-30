package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanApplicationRepository extends JpaRepository<LoanApplicationJpaEntity, UUID> {

    boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Collection<LoanApplicationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loanApplication from LoanApplicationJpaEntity loanApplication where loanApplication.id = :id")
    Optional<LoanApplicationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT nextval('loan_application_number_seq')", nativeQuery = true)
    long nextApplicationNumberSequence();
}

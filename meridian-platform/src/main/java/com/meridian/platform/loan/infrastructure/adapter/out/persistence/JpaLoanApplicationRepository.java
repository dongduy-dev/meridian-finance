package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.UUID;

public interface JpaLoanApplicationRepository extends JpaRepository<LoanApplicationJpaEntity, UUID> {

    boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Collection<LoanApplicationStatus> statuses
    );

    @Query(value = "SELECT nextval('loan_application_number_seq')", nativeQuery = true)
    long nextApplicationNumberSequence();
}

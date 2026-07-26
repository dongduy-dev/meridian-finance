package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaLoanContractRepaymentItemRepository
        extends JpaRepository<LoanContractRepaymentItemJpaEntity, UUID> {
    List<LoanContractRepaymentItemJpaEntity> findByLoanContractIdOrderByInstallmentNumberAsc(UUID loanContractId);
}

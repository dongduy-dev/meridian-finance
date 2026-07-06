package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaSalaryAdvanceLimitMovementRepository
        extends JpaRepository<SalaryAdvanceLimitMovementJpaEntity, UUID> {
    boolean existsByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    );
}

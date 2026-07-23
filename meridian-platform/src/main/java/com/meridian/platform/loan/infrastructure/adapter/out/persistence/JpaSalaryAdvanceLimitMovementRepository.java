package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

import java.util.UUID;

public interface JpaSalaryAdvanceLimitMovementRepository
        extends JpaRepository<SalaryAdvanceLimitMovementJpaEntity, UUID> {
    boolean existsByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    );

    List<SalaryAdvanceLimitMovementJpaEntity> findAllByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select movement
            from SalaryAdvanceLimitMovementJpaEntity movement
            where movement.loanApplicationId = :loanApplicationId
              and movement.movementType = :movementType
            """)
    List<SalaryAdvanceLimitMovementJpaEntity> findAllByLoanApplicationIdAndMovementTypeForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("movementType") SalaryAdvanceLimitMovementType movementType
    );

    @Query(value = """
            select coalesce(sum(
                case
                    when movement_type = 'RESERVED' then amount
                    when movement_type = 'RESERVATION_RELEASED' then -amount
                    else 0
                end
            ), 0)
            from salary_advance_limit_movements
            where salary_advance_limit_id = :salaryAdvanceLimitId
              and movement_type in ('RESERVED', 'RESERVATION_RELEASED')
            """, nativeQuery = true)
    BigDecimal calculateOutstandingReservedAmount(@Param("salaryAdvanceLimitId") UUID salaryAdvanceLimitId);
}

package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalaryAdvanceLimitMovementRepository {

    SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement);

    boolean existsByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    );

    default List<SalaryAdvanceLimitMovement> findByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    ) {
        throw new UnsupportedOperationException("Reservation evidence query is not implemented.");
    }

    default List<SalaryAdvanceLimitMovement> findByLoanApplicationIdAndMovementTypeForUpdate(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    ) {
        throw new UnsupportedOperationException("Locking reservation evidence query is not implemented.");
    }

    default BigDecimal calculateOutstandingReservedAmount(UUID salaryAdvanceLimitId) {
        throw new UnsupportedOperationException("Outstanding reservation calculation is not implemented.");
    }
}

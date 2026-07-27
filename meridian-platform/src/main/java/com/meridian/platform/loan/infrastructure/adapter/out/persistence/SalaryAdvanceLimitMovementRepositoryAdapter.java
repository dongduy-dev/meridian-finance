package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SalaryAdvanceLimitMovementRepositoryAdapter implements SalaryAdvanceLimitMovementRepository {

    private final JpaSalaryAdvanceLimitMovementRepository jpaSalaryAdvanceLimitMovementRepository;

    public SalaryAdvanceLimitMovementRepositoryAdapter(
            JpaSalaryAdvanceLimitMovementRepository jpaSalaryAdvanceLimitMovementRepository
    ) {
        this.jpaSalaryAdvanceLimitMovementRepository = jpaSalaryAdvanceLimitMovementRepository;
    }

    @Override
    public SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement) {
        return toDomain(jpaSalaryAdvanceLimitMovementRepository.save(
                new SalaryAdvanceLimitMovementJpaEntity(salaryAdvanceLimitMovement)
        ));
    }

    @Override
    public boolean existsByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    ) {
        return jpaSalaryAdvanceLimitMovementRepository.existsByLoanApplicationIdAndMovementType(
                loanApplicationId,
                movementType
        );
    }

    @Override
    public List<SalaryAdvanceLimitMovement> findByLoanApplicationIdAndMovementType(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    ) {
        return jpaSalaryAdvanceLimitMovementRepository
                .findAllByLoanApplicationIdAndMovementType(loanApplicationId, movementType)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<SalaryAdvanceLimitMovement> findByLoanApplicationIdAndMovementTypeForUpdate(
            UUID loanApplicationId,
            SalaryAdvanceLimitMovementType movementType
    ) {
        return jpaSalaryAdvanceLimitMovementRepository
                .findAllByLoanApplicationIdAndMovementTypeForUpdate(loanApplicationId, movementType)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public BigDecimal calculateOutstandingReservedAmount(UUID salaryAdvanceLimitId) {
        return jpaSalaryAdvanceLimitMovementRepository.calculateOutstandingReservedAmount(salaryAdvanceLimitId);
    }

    @Override
    public BigDecimal calculateUsedAmount(UUID salaryAdvanceLimitId) {
        return jpaSalaryAdvanceLimitMovementRepository.calculateUsedAmount(salaryAdvanceLimitId);
    }

    private SalaryAdvanceLimitMovement toDomain(SalaryAdvanceLimitMovementJpaEntity entity) {
        return new SalaryAdvanceLimitMovement(
                entity.getId(),
                entity.getSalaryAdvanceLimitId(),
                entity.getLoanApplicationId(),
                entity.getLoanAccountId(),
                entity.getMovementType(),
                entity.getAmount(),
                entity.getOccurredAt()
        );
    }
}

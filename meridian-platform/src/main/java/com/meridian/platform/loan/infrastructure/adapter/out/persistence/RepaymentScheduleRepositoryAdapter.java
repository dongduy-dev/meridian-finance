package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class RepaymentScheduleRepositoryAdapter implements RepaymentScheduleRepository {

    private final JpaRepaymentScheduleRepository repaymentSchedules;
    private final JpaRepaymentScheduleItemRepository repaymentScheduleItems;

    public RepaymentScheduleRepositoryAdapter(
            JpaRepaymentScheduleRepository repaymentSchedules,
            JpaRepaymentScheduleItemRepository repaymentScheduleItems
    ) {
        this.repaymentSchedules = repaymentSchedules;
        this.repaymentScheduleItems = repaymentScheduleItems;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RepaymentSchedule save(RepaymentSchedule repaymentSchedule) {
        if (repaymentSchedules.existsById(repaymentSchedule.id())) {
            throw conflict();
        }
        try {
            RepaymentScheduleJpaEntity saved = repaymentSchedules.saveAndFlush(
                    new RepaymentScheduleJpaEntity(repaymentSchedule)
            );
            repaymentScheduleItems.saveAllAndFlush(
                    repaymentSchedule.items().stream()
                            .map(item -> new RepaymentScheduleItemJpaEntity(
                                    repaymentSchedule.id(),
                                    item
                            ))
                            .toList()
            );
            return toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Override
    public Optional<RepaymentSchedule> findByLoanAccountId(UUID loanAccountId) {
        return repaymentSchedules.findByLoanAccountId(loanAccountId).map(this::toDomain);
    }

    @Override
    public Optional<RepaymentSchedule> findByLoanApplicationId(UUID loanApplicationId) {
        return repaymentSchedules.findByLoanApplicationId(loanApplicationId).map(this::toDomain);
    }

    @Override
    public Optional<RepaymentSchedule> findByLoanContractId(UUID loanContractId) {
        return repaymentSchedules.findByLoanContractId(loanContractId).map(this::toDomain);
    }

    private RepaymentSchedule toDomain(RepaymentScheduleJpaEntity entity) {
        return entity.toDomain(
                repaymentScheduleItems
                        .findByRepaymentScheduleIdOrderByInstallmentNumberAsc(entity.id())
                        .stream()
                        .map(RepaymentScheduleItemJpaEntity::toDomain)
                        .toList()
        );
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Final repayment schedule evidence conflicts with existing state."
        );
    }
}

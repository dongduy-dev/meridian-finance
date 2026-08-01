package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepaymentInstallmentProgressRepositoryAdapter
        implements RepaymentInstallmentProgressRepository {

    private final JpaRepaymentInstallmentProgressRepository progressRepository;

    public RepaymentInstallmentProgressRepositoryAdapter(
            JpaRepaymentInstallmentProgressRepository progressRepository
    ) {
        this.progressRepository = progressRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RepaymentInstallmentProgress> saveAll(
            List<RepaymentInstallmentProgress> progress
    ) {
        return progressRepository.saveAllAndFlush(progress.stream()
                        .map(RepaymentInstallmentProgressJpaEntity::new)
                        .toList())
                .stream()
                .map(RepaymentInstallmentProgressJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Optional<RepaymentInstallmentProgress> findByScheduleItemId(
            UUID scheduleItemId
    ) {
        return progressRepository.findById(scheduleItemId)
                .map(RepaymentInstallmentProgressJpaEntity::toDomain);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public List<RepaymentInstallmentProgress> findByRepaymentScheduleId(
            UUID scheduleId
    ) {
        return progressRepository
                .findByRepaymentScheduleIdOrderByInstallmentNumberAsc(scheduleId)
                .stream()
                .map(RepaymentInstallmentProgressJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RepaymentInstallmentProgress> findByLoanAccountIdForUpdate(
            UUID loanAccountId
    ) {
        return progressRepository.findByLoanAccountIdForUpdate(loanAccountId)
                .stream()
                .map(RepaymentInstallmentProgressJpaEntity::toDomain)
                .toList();
    }
}

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class RepaymentInstallmentStatusTransitionRepositoryAdapter
        implements RepaymentInstallmentStatusTransitionRepository {

    private final JpaRepaymentInstallmentStatusTransitionRepository transitions;

    public RepaymentInstallmentStatusTransitionRepositoryAdapter(
            JpaRepaymentInstallmentStatusTransitionRepository transitions
    ) {
        this.transitions = transitions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RepaymentInstallmentStatusTransition save(
            RepaymentInstallmentStatusTransition transition
    ) {
        return transitions.saveAndFlush(
                new RepaymentInstallmentStatusTransitionJpaEntity(transition)
        ).toDomain();
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public int nextSequenceNumber(UUID repaymentScheduleItemId) {
        return transitions.nextSequenceNumber(repaymentScheduleItemId);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public List<RepaymentInstallmentStatusTransition>
    findByRepaymentScheduleItemId(UUID repaymentScheduleItemId) {
        return transitions
                .findByRepaymentScheduleItemIdOrderBySequenceNumberAsc(
                        repaymentScheduleItemId
                )
                .stream()
                .map(RepaymentInstallmentStatusTransitionJpaEntity::toDomain)
                .toList();
    }
}

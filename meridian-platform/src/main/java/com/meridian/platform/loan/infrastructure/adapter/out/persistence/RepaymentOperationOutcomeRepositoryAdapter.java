package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class RepaymentOperationOutcomeRepositoryAdapter
        implements RepaymentOperationOutcomeRepository {
    private final JpaRepaymentOperationOutcomeRepository outcomes;
    private final ObjectMapper objectMapper;

    public RepaymentOperationOutcomeRepositoryAdapter(
            JpaRepaymentOperationOutcomeRepository outcomes,
            ObjectMapper objectMapper
    ) {
        this.outcomes = outcomes;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RepaymentOperationOutcome save(RepaymentOperationOutcome outcome) {
        if (outcomes.existsById(outcome.repaymentTransactionId())) {
            throw conflict();
        }
        try {
            String json = objectMapper.writeValueAsString(outcome);
            outcomes.saveAndFlush(new RepaymentOperationOutcomeJpaEntity(
                    outcome.repaymentTransactionId(), outcome.loanApplicationId(),
                    outcome.loanAccountId(), outcome.repaymentScheduleId(),
                    outcome.receivedAmount(), outcome.paymentValueDate(),
                    outcome.recordedAt(), outcome.principalReleased(),
                    outcome.accountStatus().name(), outcome.accountStatusChanged(), json
            ));
            return outcome;
        } catch (JacksonException exception) {
            throw conflict();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<RepaymentOperationOutcome> findByRepaymentTransactionId(
            UUID repaymentTransactionId
    ) {
        return outcomes.findById(repaymentTransactionId).map(entity -> {
            try {
                return objectMapper.readValue(
                        entity.outcomeJson(), RepaymentOperationOutcome.class
                );
            } catch (JacksonException exception) {
                throw conflict();
            }
        });
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Repayment operation outcome evidence is inconsistent."
        );
    }
}

package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionFact;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LoanApplicationStatusTransitionRecorder {

    private final LoanApplicationStatusTransitionRepository transitionRepository;

    public LoanApplicationStatusTransitionRecorder(
            LoanApplicationStatusTransitionRepository transitionRepository
    ) {
        this.transitionRepository = transitionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            BusinessOperationContext operationContext,
            List<LoanApplicationTransitionFact> facts,
            String reason
    ) {
        Objects.requireNonNull(operationContext, "operationContext must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        if (facts.isEmpty()) {
            return;
        }

        UUID loanApplicationId = facts.getFirst().loanApplicationId();
        if (facts.stream().anyMatch(fact -> !loanApplicationId.equals(fact.loanApplicationId()))) {
            throw new IllegalArgumentException("Transition batches must belong to one Loan Application.");
        }

        int sequenceNumber = transitionRepository.nextSequenceNumber(loanApplicationId);
        for (LoanApplicationTransitionFact fact : facts) {
            transitionRepository.save(new LoanApplicationStatusTransition(
                    UUID.randomUUID(),
                    loanApplicationId,
                    operationContext.operationId(),
                    sequenceNumber++,
                    fact.fromStatus(),
                    fact.toStatus(),
                    fact.action(),
                    reason,
                    operationContext.actorType(),
                    operationContext.actorUserId(),
                    operationContext.occurredAt()
            ));
        }
    }
}

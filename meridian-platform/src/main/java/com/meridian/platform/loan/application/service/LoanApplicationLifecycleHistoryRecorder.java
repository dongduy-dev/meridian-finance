package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class LoanApplicationLifecycleHistoryRecorder {

    private final LoanApplicationStatusTransitionRepository transitionRepository;

    public LoanApplicationLifecycleHistoryRecorder(LoanApplicationStatusTransitionRepository transitionRepository) {
        this.transitionRepository = transitionRepository;
    }

    public void record(
            UUID operationId,
            ActionActor actor,
            String reason,
            LocalDateTime occurredAt,
            LoanApplicationTransitionResult... results
    ) {
        short sequenceNumber = 1;
        List<LoanApplicationStatusTransition> transitions = new java.util.ArrayList<>();
        for (LoanApplicationTransitionResult result : results) {
            if (result == null || result.transition().isEmpty()) {
                continue;
            }
            transitions.add(LoanApplicationStatusTransition.from(
                    result.loanApplication().id(),
                    operationId,
                    sequenceNumber++,
                    result.transition().orElseThrow(),
                    reason,
                    actor,
                    occurredAt
            ));
        }
        if (!transitions.isEmpty()) {
            transitionRepository.saveAll(transitions);
        }
    }
}

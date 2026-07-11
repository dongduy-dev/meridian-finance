package com.meridian.platform.support;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;

import java.util.ArrayList;
import java.util.List;

public class CapturingLoanApplicationStatusTransitionRepository implements LoanApplicationStatusTransitionRepository {

    private final List<LoanApplicationStatusTransition> transitions = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void saveAll(List<LoanApplicationStatusTransition> transitions) {
        if (failure != null) {
            throw failure;
        }
        this.transitions.addAll(transitions);
    }

    public List<LoanApplicationStatusTransition> transitions() {
        return transitions;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }
}

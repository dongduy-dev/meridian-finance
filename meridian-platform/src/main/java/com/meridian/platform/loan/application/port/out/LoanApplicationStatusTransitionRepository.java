package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;

import java.util.List;

public interface LoanApplicationStatusTransitionRepository {

    void saveAll(List<LoanApplicationStatusTransition> transitions);
}

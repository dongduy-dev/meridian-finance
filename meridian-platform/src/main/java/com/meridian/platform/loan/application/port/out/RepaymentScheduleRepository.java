package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentSchedule;

import java.util.Optional;
import java.util.UUID;

public interface RepaymentScheduleRepository {

    RepaymentSchedule save(RepaymentSchedule repaymentSchedule);

    Optional<RepaymentSchedule> findByLoanAccountId(UUID loanAccountId);

    Optional<RepaymentSchedule> findByLoanApplicationId(UUID loanApplicationId);

    Optional<RepaymentSchedule> findByLoanContractId(UUID loanContractId);
}

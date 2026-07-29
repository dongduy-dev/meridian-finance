package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentInstallmentProgressRepository {

    List<RepaymentInstallmentProgress> saveAll(
            List<RepaymentInstallmentProgress> progress
    );

    Optional<RepaymentInstallmentProgress> findByScheduleItemId(UUID scheduleItemId);

    List<RepaymentInstallmentProgress> findByRepaymentScheduleId(UUID scheduleId);

    List<RepaymentInstallmentProgress> findByLoanAccountIdForUpdate(UUID loanAccountId);
}

package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;

import java.util.List;
import java.util.UUID;

public interface RepaymentInstallmentStatusTransitionRepository {

    RepaymentInstallmentStatusTransition save(
            RepaymentInstallmentStatusTransition transition
    );

    int nextSequenceNumber(UUID repaymentScheduleItemId);

    List<RepaymentInstallmentStatusTransition> findByRepaymentScheduleItemId(
            UUID repaymentScheduleItemId
    );

    List<RepaymentInstallmentStatusTransition> findByOperationId(UUID operationId);
}

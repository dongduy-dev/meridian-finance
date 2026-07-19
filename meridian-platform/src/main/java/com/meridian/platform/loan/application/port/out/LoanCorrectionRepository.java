package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanCorrectionRepository {
    LoanCorrectionRequest saveRequest(LoanCorrectionRequest request);

    LoanCorrectionTask saveTask(LoanCorrectionTask task);

    Optional<LoanCorrectionRequest> findActiveRequestByApplicationIdForUpdate(UUID loanApplicationId);

    Optional<LoanCorrectionRequest> findLatestRequestByApplicationId(UUID loanApplicationId);

    Optional<LoanCorrectionRequest> findRequestById(UUID correctionRequestId);

    Optional<LoanCorrectionTask> findTaskByIdForUpdate(UUID taskId);

    List<LoanCorrectionTask> findTasksByRequestIdForUpdate(UUID correctionRequestId);

    List<LoanCorrectionTask> findCustomerTasks(UUID loanApplicationId, UUID customerId);

    Optional<LoanCorrectionTask> findOpenCustomerDocumentTask(
            UUID loanApplicationId,
            UUID checklistItemId
    );
}

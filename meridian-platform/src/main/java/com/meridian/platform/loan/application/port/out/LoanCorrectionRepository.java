package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanCorrectionRepository {
    LoanCorrectionRequest saveRequest(LoanCorrectionRequest request);

    LoanCorrectionTask saveTask(LoanCorrectionTask task);

    Optional<LoanCorrectionRequest> findActiveRequestByApplicationIdForUpdate(UUID loanApplicationId);

    boolean existsActiveRequestByApplicationId(UUID loanApplicationId);

    Optional<LoanCorrectionRequest> findLatestRequestByApplicationId(UUID loanApplicationId);

    Optional<LoanCorrectionRequest> findRequestById(UUID correctionRequestId);

    Optional<LoanCorrectionTask> findTaskByIdForUpdate(UUID taskId);

    List<LoanCorrectionTask> findTasksByRequestIdForUpdate(UUID correctionRequestId);

    List<LoanCorrectionTask> findTasksByRequestId(UUID correctionRequestId);

    List<LoanCorrectionTask> findCustomerTasks(UUID loanApplicationId, UUID customerId);

    boolean existsTaskByRequestIdAndResponsibleParty(
            UUID correctionRequestId,
            LoanCorrectionResponsibility responsibleParty
    );

    Optional<LoanCorrectionTask> findOpenCustomerDocumentTask(
            UUID loanApplicationId,
            UUID checklistItemId
    );

    List<LoanCorrectionTask> findStaffTasks(LoanCorrectionTaskStatus status, int page, int size);

    Optional<LoanCorrectionTask> findOpenStaffDocumentTask(
            UUID loanApplicationId,
            UUID checklistItemId
    );

    int nextTaskSequence(UUID correctionRequestId);
}

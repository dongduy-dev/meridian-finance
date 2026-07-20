package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class LoanCorrectionRepositoryAdapter implements LoanCorrectionRepository {
    private final JpaLoanCorrectionRequestRepository requestRepository;
    private final JpaLoanCorrectionTaskRepository taskRepository;

    public LoanCorrectionRepositoryAdapter(
            JpaLoanCorrectionRequestRepository requestRepository,
            JpaLoanCorrectionTaskRepository taskRepository
    ) {
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public LoanCorrectionRequest saveRequest(LoanCorrectionRequest request) {
        LoanCorrectionRequestJpaEntity entity = requestRepository.findById(request.id())
                .orElseGet(() -> new LoanCorrectionRequestJpaEntity(request));
        entity.updateFrom(request);
        return requestRepository.save(entity).toDomain();
    }

    @Override
    public LoanCorrectionTask saveTask(LoanCorrectionTask task) {
        LoanCorrectionTaskJpaEntity entity = taskRepository.findById(task.id())
                .orElseGet(() -> new LoanCorrectionTaskJpaEntity(task));
        entity.updateFrom(task);
        return taskRepository.save(entity).toDomain();
    }

    @Override
    public Optional<LoanCorrectionRequest> findActiveRequestByApplicationIdForUpdate(UUID loanApplicationId) {
        return requestRepository.findActiveForUpdate(loanApplicationId, Set.of(
                LoanCorrectionRequestStatus.OPEN,
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION
        )).map(LoanCorrectionRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanCorrectionRequest> findLatestRequestByApplicationId(UUID loanApplicationId) {
        return requestRepository.findFirstByLoanApplicationIdOrderByCreatedAtDesc(loanApplicationId)
                .map(LoanCorrectionRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanCorrectionRequest> findRequestById(UUID correctionRequestId) {
        return requestRepository.findById(correctionRequestId)
                .map(LoanCorrectionRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanCorrectionTask> findTaskByIdForUpdate(UUID taskId) {
        return taskRepository.findByIdForUpdate(taskId).map(LoanCorrectionTaskJpaEntity::toDomain);
    }

    @Override
    public List<LoanCorrectionTask> findTasksByRequestIdForUpdate(UUID correctionRequestId) {
        return taskRepository.findAllByRequestIdForUpdate(correctionRequestId).stream()
                .map(LoanCorrectionTaskJpaEntity::toDomain).toList();
    }

    @Override
    public List<LoanCorrectionTask> findCustomerTasks(UUID loanApplicationId, UUID customerId) {
        return taskRepository.findCustomerQueue(
                loanApplicationId, LoanCorrectionResponsibility.CUSTOMER
        ).stream().map(LoanCorrectionTaskJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<LoanCorrectionTask> findOpenCustomerDocumentTask(UUID loanApplicationId, UUID checklistItemId) {
        return taskRepository.findOpenCustomerDocumentTask(
                        loanApplicationId,
                        checklistItemId,
                        Set.of(LoanCorrectionRequestStatus.OPEN, LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION),
                        LoanCorrectionResponsibility.CUSTOMER,
                        LoanCorrectionTaskStatus.OPEN
                )
                .map(LoanCorrectionTaskJpaEntity::toDomain);
    }
    @Override
    public List<LoanCorrectionTask> findStaffTasks(LoanCorrectionTaskStatus status, int page, int size) {
        return taskRepository.findStaffQueue(
                Set.of(LoanCorrectionRequestStatus.OPEN, LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION),
                LoanCorrectionResponsibility.STAFF,
                status,
                PageRequest.of(page, size)
        ).stream().map(LoanCorrectionTaskJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<LoanCorrectionTask> findOpenStaffDocumentTask(
            UUID loanApplicationId,
            UUID checklistItemId
    ) {
        return taskRepository.findOpenStaffDocumentTask(
                        loanApplicationId,
                        checklistItemId,
                        Set.of(LoanCorrectionRequestStatus.OPEN, LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION),
                        LoanCorrectionResponsibility.STAFF,
                        LoanCorrectionTaskStatus.OPEN,
                        LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD
                )
                .map(LoanCorrectionTaskJpaEntity::toDomain);
    }

    @Override
    public int nextTaskSequence(UUID correctionRequestId) {
        return taskRepository.nextTaskSequence(correctionRequestId);
    }

}

package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanCorrectionTaskRepository extends JpaRepository<LoanCorrectionTaskJpaEntity, UUID> {
    boolean existsByCorrectionRequestIdAndResponsibleParty(
            UUID correctionRequestId,
            LoanCorrectionResponsibility responsibleParty
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from LoanCorrectionTaskJpaEntity task where task.id = :id")
    Optional<LoanCorrectionTaskJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from LoanCorrectionTaskJpaEntity task where task.correctionRequestId = :requestId order by task.taskSequence, task.id")
    List<LoanCorrectionTaskJpaEntity> findAllByRequestIdForUpdate(@Param("requestId") UUID correctionRequestId);

    @Query("select task from LoanCorrectionTaskJpaEntity task join LoanCorrectionRequestJpaEntity request on request.id = task.correctionRequestId where request.loanApplicationId = :applicationId and request.status in :requestStatuses and task.responsibleParty = :responsibility order by task.createdAt, task.id")
    List<LoanCorrectionTaskJpaEntity> findCustomerQueue(
            @Param("applicationId") UUID loanApplicationId,
            @Param("requestStatuses") java.util.Collection<com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus> requestStatuses,
            @Param("responsibility") LoanCorrectionResponsibility responsibility
    );

    @Query("select task from LoanCorrectionTaskJpaEntity task join LoanCorrectionRequestJpaEntity request on request.id = task.correctionRequestId where request.loanApplicationId = :applicationId and request.status in :requestStatuses and task.responsibleParty = :responsibility and task.status = :taskStatus and task.checklistItemId = :itemId")
    Optional<LoanCorrectionTaskJpaEntity> findOpenCustomerDocumentTask(
            @Param("applicationId") UUID loanApplicationId,
            @Param("itemId") UUID checklistItemId,
            @Param("requestStatuses") java.util.Collection<com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus> requestStatuses,
            @Param("responsibility") LoanCorrectionResponsibility responsibility,
            @Param("taskStatus") LoanCorrectionTaskStatus taskStatus
    );

    @Query("select task from LoanCorrectionTaskJpaEntity task join LoanCorrectionRequestJpaEntity request on request.id = task.correctionRequestId where request.status in :requestStatuses and task.responsibleParty = :responsibility and task.status = :taskStatus order by task.createdAt, task.id")
    List<LoanCorrectionTaskJpaEntity> findStaffQueue(
            @Param("requestStatuses") java.util.Collection<com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus> requestStatuses,
            @Param("responsibility") LoanCorrectionResponsibility responsibility,
            @Param("taskStatus") LoanCorrectionTaskStatus taskStatus,
            Pageable pageable
    );

    @Query("select task from LoanCorrectionTaskJpaEntity task join LoanCorrectionRequestJpaEntity request on request.id = task.correctionRequestId where request.loanApplicationId = :applicationId and request.status in :requestStatuses and task.responsibleParty = :responsibility and task.status = :taskStatus and task.scope = :scope and task.checklistItemId = :itemId")
    Optional<LoanCorrectionTaskJpaEntity> findOpenStaffDocumentTask(
            @Param("applicationId") UUID loanApplicationId,
            @Param("itemId") UUID checklistItemId,
            @Param("requestStatuses") java.util.Collection<com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus> requestStatuses,
            @Param("responsibility") LoanCorrectionResponsibility responsibility,
            @Param("taskStatus") LoanCorrectionTaskStatus taskStatus,
            @Param("scope") LoanCorrectionScope scope
    );

    @Query("select coalesce(max(task.taskSequence), 0) + 1 from LoanCorrectionTaskJpaEntity task where task.correctionRequestId = :requestId")
    int nextTaskSequence(@Param("requestId") UUID correctionRequestId);
}

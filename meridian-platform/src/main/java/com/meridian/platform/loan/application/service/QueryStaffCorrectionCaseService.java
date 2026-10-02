package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffCorrectionCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.AssistedActionEvidenceMetadataDto;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionCaseUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.shared.domain.model.ActorType;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffCorrectionCaseService implements QueryStaffCorrectionCaseUseCase {
    private final LoanApplicationRepository applications;
    private final LoanCorrectionRepository corrections;
    private final LoanApplicationCancellationRepository cancellations;
    private final LoanAssistedActionEvidencePort assistedEvidence;
    private final LoanDocumentChecklistPort documents;
    private final CustomerCorrectionDocumentProof customerDocumentProof;
    private final AssistedCustomerActionProvenanceComposer provenance;
    private final CurrentUserProvider currentUserProvider;
    private final WorkflowActorDirectoryPort actors;
    private final LoanApplicationStatusTransitionRepository transitions;

    public QueryStaffCorrectionCaseService(
            LoanApplicationRepository applications,
            LoanCorrectionRepository corrections,
            LoanApplicationCancellationRepository cancellations,
            LoanAssistedActionEvidencePort assistedEvidence,
            LoanDocumentChecklistPort documents,
            CustomerCorrectionDocumentProof customerDocumentProof,
            AssistedCustomerActionProvenanceComposer provenance,
            CurrentUserProvider currentUserProvider,
            WorkflowActorDirectoryPort actors,
            LoanApplicationStatusTransitionRepository transitions
    ) {
        this.applications = applications;
        this.corrections = corrections;
        this.cancellations = cancellations;
        this.assistedEvidence = assistedEvidence;
        this.documents = documents;
        this.customerDocumentProof = customerDocumentProof;
        this.provenance = provenance;
        this.currentUserProvider = currentUserProvider;
        this.actors = actors;
        this.transitions = transitions;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffCorrectionCaseDto query(UUID loanApplicationId) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        List<LoanCorrectionRequest> requests = corrections.findRequestsByApplicationId(loanApplicationId);
        LoanCorrectionRequest request = corrections.findLatestRequestByApplicationId(loanApplicationId).orElse(null);
        return new StaffCorrectionCaseDto(
                application.id(), application.applicationNumber(), application.productCode().name(),
                application.originationChannel().name(), application.status().name(),
                request == null ? null : toRequest(application, request, actor),
                history(application, requests),
                assistedCancellation(application, request, actor));
    }

    private List<StaffCorrectionCaseDto.HistoricalRequestDto> history(
            LoanApplication application, List<LoanCorrectionRequest> requests) {
        if (requests.isEmpty()) return List.of();
        var lifecycle = transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(application.id());
        Map<UUID, List<LoanCorrectionTask>> tasks = new LinkedHashMap<>();
        Set<UUID> actorIds = new HashSet<>();
        requests.forEach(request -> {
            if (!application.id().equals(request.loanApplicationId())) throw historyConflict();
            if (request.createdByUserId() != null) actorIds.add(request.createdByUserId());
            var rows = corrections.findTasksByRequestId(request.id()).stream()
                    .sorted(Comparator.comparingInt(LoanCorrectionTask::sequence).thenComparing(LoanCorrectionTask::id)).toList();
            if (rows.stream().anyMatch(task -> !request.id().equals(task.correctionRequestId()))) throw historyConflict();
            rows.stream().map(LoanCorrectionTask::completedByUserId).filter(Objects::nonNull).forEach(actorIds::add);
            tasks.put(request.id(), rows);
        });
        Map<UUID, LoanApplicationStatusTransition> resubmissions = new LinkedHashMap<>();
        requests.forEach(request -> {
            if (request.resubmittedAt() == null || request.status() != LoanCorrectionRequestStatus.RESUBMITTED
                    || requests.stream().filter(other -> request.resubmittedAt().equals(other.resubmittedAt())).count() != 1) return;
            var matches = lifecycle.stream().filter(row -> application.id().equals(row.loanApplicationId())
                    && row.action() == LoanApplicationTransitionAction.RESUBMIT_CORRECTION
                    && request.resubmittedAt().equals(row.occurredAt())).toList();
            if (matches.size() != 1) return;
            var row = matches.getFirst();
            if (row.fromStatus() != LoanApplicationStatus.RETURNED_FOR_REVISION
                    || (row.toStatus() != LoanApplicationStatus.SUBMITTED && row.toStatus() != LoanApplicationStatus.UNDER_REVIEW)) return;
            resubmissions.put(request.id(), row);
            if (row.actorUserId() != null) actorIds.add(row.actorUserId());
        });
        var summaries = actors.findByUserIds(actorIds);
        return requests.stream().map(request -> {
            var row = resubmissions.get(request.id());
            var resubmitter = row == null ? unavailable() : row.actorType() == ActorType.SYSTEM
                    ? new StaffCorrectionCaseDto.ActorDto("SYSTEM", null)
                    : resolveActor(application, row.actorUserId(), summaries,
                            tasks.get(request.id()).stream().noneMatch(task -> task.responsibleParty() == LoanCorrectionResponsibility.STAFF), true);
            return new StaffCorrectionCaseDto.HistoricalRequestDto(request.id(), request.status().name(),
                    request.reasonCode().name(), request.sourceAction(), request.sourceReviewCycleId(),
                    resolveActor(application, request.createdByUserId(), summaries, false, true), request.createdAt(), request.readyAt(),
                    request.resubmittedAt(), request.cancelledAt(), request.resubmittedAt() == null ? null : resubmitter,
                    row == null ? null : row.toStatus().name(), tasks.get(request.id()).stream().map(task ->
                    new StaffCorrectionCaseDto.HistoricalTaskDto(task.id(), task.sequence(), task.responsibleParty().name(),
                            task.scope().name(), task.documentType() == null ? null : task.documentType().name(),
                            task.checklistItemId(), task.baselineDocumentVersionId(), task.customerInstruction(),
                            task.staffInstruction(), task.createdAt(), task.status().name(),
                            task.completedAt() == null ? null : resolveActor(application, task.completedByUserId(), summaries,
                                    task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER,
                                    task.responsibleParty() == LoanCorrectionResponsibility.STAFF || application.permitsStaffMediatedCustomerCorrection()),
                            task.completedAt())).toList());
        }).toList();
    }

    private static StaffCorrectionCaseDto.ActorDto resolveActor(LoanApplication application, UUID id,
            Map<UUID, WorkflowActorDirectoryPort.ActorSummary> actors, boolean allowCustomer, boolean allowStaff) {
        var actor = id == null ? null : actors.get(id);
        if (actor == null) return unavailable();
        if (!id.equals(actor.userId())) throw historyConflict();
        if ("CUSTOMER".equals(actor.userType())) {
            if (!allowCustomer || !application.customerId().equals(actor.customerId()) || actor.staff() != null
                    || application.originationChannel() != OriginationChannel.CUSTOMER_DIGITAL) throw historyConflict();
            return new StaffCorrectionCaseDto.ActorDto("CUSTOMER_SELF_SERVICE", null);
        }
        if (!"STAFF".equals(actor.userType())) return unavailable();
        if (!allowStaff || actor.customerId() != null) throw historyConflict();
        var staff = actor.staff();
        if (staff == null || !id.equals(staff.userId()) || staff.displayName() == null || staff.displayName().isBlank()
                || staff.email() == null || staff.email().isBlank()) return unavailable();
        return new StaffCorrectionCaseDto.ActorDto("STAFF", new StaffLoanApplicationCaseDto.StaffActorDto(id, staff.displayName(), staff.email()));
    }

    private static StaffCorrectionCaseDto.ActorDto unavailable() {
        return new StaffCorrectionCaseDto.ActorDto("UNAVAILABLE", null);
    }

    private static BusinessStateConflictException historyConflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", "Correction history evidence is inconsistent.");
    }

    private StaffCorrectionCaseDto.AssistedCancellationDto assistedCancellation(
            LoanApplication application,
            LoanCorrectionRequest request,
            AuthenticatedUser actor
    ) {
        var completed = provenance.cancellation(application, request);
        boolean eligible = request != null
                && application.originationChannel() == OriginationChannel.STAFF_ASSISTED
                && application.productCode() == ProductCode.UNSECURED_CONSUMER_LOAN
                && application.status() == LoanApplicationStatus.RETURNED_FOR_REVISION
                && (request.status() == LoanCorrectionRequestStatus.OPEN
                || request.status() == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION)
                && cancellations.findByLoanApplicationId(application.id()).isEmpty();
        if (!eligible) {
            return new StaffCorrectionCaseDto.AssistedCancellationDto(
                    false, completed == null ? null : request.id(),
                    completed == null ? null : completed.evidence(), false, false, completed);
        }
        AssistedActionEvidenceMetadataDto evidence = assistedEvidence
                .findCancellationEvidence(application.id(), request.id())
                .map(QueryAssistedApprovedOfferResponseService::toDto)
                .orElse(null);
        boolean loanOfficer = actor.roles().contains("LOAN_OFFICER");
        boolean commandAuthority = loanOfficer && actor.hasPermission("loan:cancel:staff");
        return new StaffCorrectionCaseDto.AssistedCancellationDto(
                true,
                request.id(),
                evidence,
                commandAuthority && actor.hasPermission("document:upload:assisted-action"),
                commandAuthority && evidence != null,
                null
        );
    }

    private StaffCorrectionCaseDto.CorrectionRequestDto toRequest(
            LoanApplication application,
            LoanCorrectionRequest request,
            AuthenticatedUser actor
    ) {
        List<LoanCorrectionTask> tasks = corrections.findTasksByRequestId(request.id());
        boolean hasStaffTasks = tasks.stream()
                .anyMatch(task -> task.responsibleParty() == LoanCorrectionResponsibility.STAFF);
        boolean hasCustomerTasks = tasks.stream()
                .anyMatch(task -> task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER);
        boolean assistedCustomerTasks = hasCustomerTasks
                && application.permitsStaffMediatedCustomerCorrection();
        boolean allComplete = !tasks.isEmpty() && tasks.stream()
                .allMatch(task -> task.status() == LoanCorrectionTaskStatus.COMPLETED);
        boolean makerCheckerBlocked = hasStaffTasks
                && request.createdByUserId().equals(actor.userId());
        return new StaffCorrectionCaseDto.CorrectionRequestDto(
                request.id(), request.status().name(), request.reasonCode().name(), request.createdAt(),
                makerCheckerBlocked, allComplete,
                (hasStaffTasks || assistedCustomerTasks)
                        && request.status() == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION
                        && allComplete,
                tasks.stream().map(task -> toTask(
                        application, request, task, tasks, actor, makerCheckerBlocked)).toList());
    }

    private StaffCorrectionCaseDto.TaskDto toTask(
            LoanApplication application,
            LoanCorrectionRequest request,
            LoanCorrectionTask task,
            List<LoanCorrectionTask> requestTasks,
            AuthenticatedUser actor,
            boolean makerCheckerBlocked
    ) {
        boolean customerSourceViaStaff = task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
                && application.permitsStaffMediatedCustomerCorrection();
        String proofState = proofState(application, task, requestTasks, customerSourceViaStaff);
        boolean open = task.status() == LoanCorrectionTaskStatus.OPEN;
        boolean uploadActionAvailable = open
                && task.scope() != LoanCorrectionScope.DOCUMENT_REVIEW
                && ((customerSourceViaStaff
                && actor.hasPermission("document:upload:assisted-correction"))
                || (task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                && actor.hasPermission("document:upload:staff")));
        boolean completionActionAvailable = open
                && "SATISFIED".equals(proofState)
                && (customerSourceViaStaff
                || (task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                && !makerCheckerBlocked));
        return new StaffCorrectionCaseDto.TaskDto(
                task.id(), task.responsibleParty().name(), task.status().name(), task.scope().name(),
                task.documentType() == null ? null : task.documentType().name(), task.checklistItemId(),
                task.baselineDocumentVersionId(), request.reasonCode().name(),
                customerSourceViaStaff ? task.customerInstruction() : null,
                task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                        ? task.staffInstruction() : null,
                task.createdAt(), task.completedAt(), proofState, customerSourceViaStaff,
                uploadActionAvailable, completionActionAvailable);
    }

    private String proofState(
            LoanApplication application,
            LoanCorrectionTask task,
            List<LoanCorrectionTask> requestTasks,
            boolean customerSourceViaStaff
    ) {
        if (task.responsibleParty() != LoanCorrectionResponsibility.STAFF && !customerSourceViaStaff) {
            return "NOT_APPLICABLE";
        }
        if (task.status() == LoanCorrectionTaskStatus.COMPLETED) return "SATISFIED";
        if (customerSourceViaStaff) {
            return customerDocumentProof.isSatisfied(application.id(), task) ? "SATISFIED" : "MISSING";
        }
        try {
            if (task.scope() == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
                documents.requireCurrentVersion(application.id(), task.checklistItemId());
                return "SATISFIED";
            }
            if (task.scope() == LoanCorrectionScope.DOCUMENT_REVIEW) {
                boolean followsReplacement = requestTasks.stream().anyMatch(candidate ->
                        candidate.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
                                && candidate.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                                && Objects.equals(candidate.checklistItemId(), task.checklistItemId())
                                && Objects.equals(candidate.baselineDocumentVersionId(),
                                task.baselineDocumentVersionId()));
                UUID versionToReview = task.baselineDocumentVersionId();
                if (followsReplacement) {
                    versionToReview = documents.requireCurrentVersion(
                            application.id(), task.checklistItemId());
                    if (Objects.equals(versionToReview, task.baselineDocumentVersionId())) return "MISSING";
                }
                return documents.isVersionReviewed(
                        application.id(), task.checklistItemId(), versionToReview)
                        ? "SATISFIED" : "MISSING";
            }
            return "MISSING";
        } catch (BusinessStateConflictException exception) {
            if ("DOCUMENT_UPLOAD_REQUIRED".equals(exception.getErrorCode())) return "MISSING";
            throw exception;
        }
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:correction:staff")) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED", "Staff correction permission is required.");
        }
    }
}
